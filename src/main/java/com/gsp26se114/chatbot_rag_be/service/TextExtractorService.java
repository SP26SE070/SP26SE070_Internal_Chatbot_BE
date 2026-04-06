package com.gsp26se114.chatbot_rag_be.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Trích text để nhúng RAG: PDF, DOCX (gồm cả bảng), XLSX, PPTX cơ bản, TXT/MD/CSV.
 * <p>
 * Trước đây DOCX chỉ đọc {@link XWPFDocument#getParagraphs()} — nội dung nằm trong <b>bảng</b> Word
 * không được lấy → embedding báo "No text extracted" dù trên màn hình vẫn thấy đầy chữ.
 */
@Service
@Slf4j
public class TextExtractorService {

    /**
     * @param originalFileName tên gốc (để suy MIME khi client gửi sai / octet-stream)
     */
    public String extractText(byte[] fileContent, String fileType, String originalFileName) {
        String effective = resolveEffectiveContentType(fileType, originalFileName);
        try {
            return switch (effective) {
                case "application/pdf" -> extractFromPdf(fileContent);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> extractFromDocx(fileContent);
                case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> extractFromXlsx(fileContent);
                case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> extractFromPptx(fileContent);
                case "text/plain", "text/markdown", "text/csv" -> extractFromText(fileContent);
                default -> {
                    log.warn("Unsupported file type for text extraction: {} (effective: {})", fileType, effective);
                    yield "";
                }
            };
        } catch (Exception e) {
            log.error("Failed to extract text from file type: {} (effective: {})", fileType, effective, e);
            throw new RuntimeException("Text extraction failed: " + e.getMessage(), e);
        }
    }

    /** @deprecated dùng {@link #extractText(byte[], String, String)} để hỗ trợ MIME + tên file */
    @Deprecated
    public String extractText(byte[] fileContent, String fileType) {
        return extractText(fileContent, fileType, null);
    }

    /**
     * Chuẩn hóa MIME: một số trình duyệt gửi sai; khi đó suy từ đuôi file.
     */
    static String resolveEffectiveContentType(String fileType, String originalFileName) {
        String ft = fileType == null ? "" : fileType.trim().toLowerCase(Locale.ROOT);
        if (!ft.isEmpty()
                && !"application/octet-stream".equals(ft)
                && !"binary/octet-stream".equals(ft)) {
            return ft;
        }
        String ext = extractExtension(originalFileName);
        if (ext == null) {
            return ft.isEmpty() ? "application/octet-stream" : ft;
        }
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain";
            case "md" -> "text/markdown";
            case "csv" -> "text/csv";
            default -> ft.isEmpty() ? "application/octet-stream" : ft;
        };
    }

    private static String extractExtension(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return null;
        }
        String name = originalFileName.trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Giữ xuống dòng mềm (Shift+Enter / {@code w:br}) và tab trong một đoạn Word.
     */
    private static String paragraphTextPreservingLineBreaks(XWPFParagraph p) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : p.getRuns()) {
            String t = run.getText(0);
            if (t != null) {
                sb.append(t);
            }
            CTR ctr = run.getCTR();
            if (ctr == null) {
                continue;
            }
            int tabs = ctr.sizeOfTabArray();
            for (int i = 0; i < tabs; i++) {
                sb.append('\t');
            }
            int breaks = ctr.sizeOfBrArray();
            for (int i = 0; i < breaks; i++) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String extractFromPdf(byte[] fileContent) throws IOException {
        try (PDDocument document = Loader.loadPDF(fileContent)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            String text = stripper.getText(document);
            log.debug("Extracted {} characters from PDF", text.length());
            return cleanText(text);
        }
    }

    /**
     * DOCX: đọc toàn bộ phần thân theo thứ tự — đoạn văn <b>và</b> bảng (ô nối bằng tab, hàng xuống dòng).
     */
    private String extractFromDocx(byte[] fileContent) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileContent))) {
            StringBuilder text = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    String t = paragraphTextPreservingLineBreaks(p);
                    text.append(t.stripTrailing()).append('\n');
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            String cellText = cell.getText();
                            if (cellText != null && !cellText.isBlank()) {
                                text.append(cellText).append('\t');
                            }
                        }
                        text.append('\n');
                    }
                }
            }
            log.debug("Extracted {} characters from DOCX (paragraphs + tables)", text.length());
            return cleanText(text.toString());
        }
    }

    private String extractFromXlsx(byte[] fileContent) throws IOException {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(fileContent))) {
            DataFormatter fmt = new DataFormatter();
            StringBuilder sb = new StringBuilder();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheet == null) {
                    continue;
                }
                for (Row row : sheet) {
                    if (row == null) {
                        continue;
                    }
                    for (Cell cell : row) {
                        sb.append(fmt.formatCellValue(cell)).append('\t');
                    }
                    sb.append('\n');
                }
            }
            log.debug("Extracted {} characters from XLSX", sb.length());
            return cleanText(sb.toString());
        }
    }

    private String extractFromPptx(byte[] fileContent) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(fileContent))) {
            StringBuilder sb = new StringBuilder();
            for (XSLFSlide slide : ppt.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        String t = ts.getText();
                        if (t != null && !t.isBlank()) {
                            sb.append(t).append('\n');
                        }
                    }
                }
            }
            log.debug("Extracted {} characters from PPTX", sb.length());
            return cleanText(sb.toString());
        }
    }

    private String extractFromText(byte[] fileContent) {
        String text = new String(fileContent, StandardCharsets.UTF_8);
        log.debug("Extracted {} characters from text file", text.length());
        return cleanText(text);
    }

    /**
     * Chuẩn hóa khoảng trắng <b>theo từng dòng</b>: giữ xuống dòng / phân đoạn; không gộp cả tài liệu thành một dòng.
     */
    private String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            String line = lines[i].replace('\u00a0', ' ');
            line = line.replaceAll("[ \t]+", " ");
            sb.append(line);
        }
        String result = sb.toString().replaceAll("\n{3,}", "\n\n");
        return result.trim();
    }
}

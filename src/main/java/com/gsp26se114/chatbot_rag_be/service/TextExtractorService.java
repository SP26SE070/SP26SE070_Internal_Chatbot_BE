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
import org.apache.poi.xslf.usermodel.XSLFNotes;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Trích text để nhúng RAG: PDF, DOCX (gồm cả bảng), XLSX, PPTX cơ bản, TXT/MD/CSV.
 * <p>
 * Trước đây DOCX chỉ đọc {@link XWPFDocument#getParagraphs()} — nội dung nằm trong <b>bảng</b> Word
 * không được lấy → embedding báo "No text extracted" dù trên màn hình vẫn thấy đầy chữ.
 */
@Service
@Slf4j
public class TextExtractorService {

    /** Đánh dấu ranh giới sheet cho {@link ChunkingService} (nhóm dòng / không cắt giữa hàng). */
    public static final String XLSX_SHEET_MARKER = "\n\n### SHEET: ";
    public static final String XLSX_SHEET_MARKER_END = " ###\n";

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
            int pages = document.getNumberOfPages();
            if (pages <= 0) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                sb.append(stripper.getText(document));
                if (page < pages) {
                    sb.append('\f');
                }
            }
            log.debug("Extracted {} characters from PDF ({} pages, form-feed between pages)", sb.length(), pages);
            return cleanText(sb.toString());
        }
    }

    /**
     * DOCX: đọc toàn bộ phần thân theo thứ tự — đoạn văn <b>và</b> bảng (ô nối bằng tab, hàng xuống dòng).
     */
    private static Integer docxHeadingLevel(XWPFParagraph p) {
        String styleId = p.getStyle();
        if (styleId == null || styleId.isBlank()) {
            return null;
        }
        String low = styleId.toLowerCase(Locale.ROOT);
        if (!low.contains("heading")) {
            return null;
        }
        String digits = styleId.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return 1;
        }
        try {
            int v = Integer.parseInt(digits.substring(0, 1));
            return Math.min(9, Math.max(1, v));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String extractFromDocx(byte[] fileContent) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileContent))) {
            StringBuilder text = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    Integer lvl = docxHeadingLevel(p);
                    String t = paragraphTextPreservingLineBreaks(p).stripTrailing();
                    if (lvl != null && !t.isBlank()) {
                        text.append("<<<DOCX_H:").append(lvl).append(">>>\n");
                        text.append(t).append("\n\n");
                    } else if (!t.isBlank()) {
                        text.append(t).append("\n\n");
                    } else {
                        text.append("\n\n");
                    }
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
                    text.append("\n\n");
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
                String sheetName = sheet.getSheetName() != null ? sheet.getSheetName() : ("Sheet" + (s + 1));
                sb.append(XLSX_SHEET_MARKER).append(sheetName).append(XLSX_SHEET_MARKER_END);
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
            List<XSLFSlide> slides = ppt.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                if (i > 0) {
                    sb.append('\f');
                }
                List<String> lines = new ArrayList<>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        String t = ts.getText();
                        if (t != null && !t.isBlank()) {
                            for (String ln : t.split("\\R")) {
                                String s = ln.strip();
                                if (!s.isEmpty()) {
                                    lines.add(s);
                                }
                            }
                        }
                    }
                }
                sb.append("Slide ").append(i + 1).append(": ");
                if (!lines.isEmpty()) {
                    sb.append(lines.get(0)).append('\n');
                    if (lines.size() > 1) {
                        sb.append(String.join("\n", lines.subList(1, lines.size()))).append('\n');
                    }
                } else {
                    sb.append('\n');
                }
                try {
                    XSLFNotes notes = slide.getNotes();
                    if (notes != null) {
                        StringBuilder noteText = new StringBuilder();
                        for (XSLFShape sh : notes.getShapes()) {
                            if (sh instanceof XSLFTextShape nts) {
                                String nt = nts.getText();
                                if (nt != null && !nt.isBlank()) {
                                    if (noteText.length() > 0) {
                                        noteText.append('\n');
                                    }
                                    noteText.append(nt.strip());
                                }
                            }
                        }
                        if (noteText.length() > 0) {
                            sb.append("Notes: ").append(noteText).append('\n');
                        }
                    }
                } catch (Exception ex) {
                    log.trace("No notes for slide {}: {}", i + 1, ex.getMessage());
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

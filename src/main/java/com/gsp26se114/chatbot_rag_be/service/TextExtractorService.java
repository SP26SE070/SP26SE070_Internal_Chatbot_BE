package com.gsp26se114.chatbot_rag_be.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Service để extract text từ các file formats (PDF, DOCX, TXT)
 */
@Service
@Slf4j
public class TextExtractorService {

    /**
     * Extract text dựa vào file type
     */
    public String extractText(byte[] fileContent, String fileType) {
        try {
            return switch (fileType.toLowerCase()) {
                case "application/pdf" -> extractFromPdf(fileContent);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> extractFromDocx(fileContent);
                case "text/plain", "text/markdown" -> extractFromText(fileContent);
                default -> {
                    log.warn("Unsupported file type for text extraction: {}", fileType);
                    yield "";
                }
            };
        } catch (Exception e) {
            log.error("Failed to extract text from file type: {}", fileType, e);
            throw new RuntimeException("Text extraction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extract text từ PDF
     */
    private String extractFromPdf(byte[] fileContent) throws IOException {
        try (PDDocument document = Loader.loadPDF(fileContent)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.debug("Extracted {} characters from PDF", text.length());
            return cleanText(text);
        }
    }

    /**
     * Extract text từ DOCX
     */
    private String extractFromDocx(byte[] fileContent) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileContent))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n");
            }
            log.debug("Extracted {} characters from DOCX", text.length());
            return cleanText(text.toString());
        }
    }

    /**
     * Extract text từ plain text file
     */
    private String extractFromText(byte[] fileContent) {
        String text = new String(fileContent);
        log.debug("Extracted {} characters from text file", text.length());
        return cleanText(text);
    }

    /**
     * Clean text: xóa ký tự thừa, normalize spaces
     */
    private String cleanText(String text) {
        return text
                .replaceAll("\\s+", " ")  // Multiple spaces → single space
                .replaceAll("\\n{3,}", "\n\n")  // Multiple newlines → max 2
                .trim();
    }
}

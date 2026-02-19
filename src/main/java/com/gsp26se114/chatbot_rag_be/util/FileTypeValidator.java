package com.gsp26se114.chatbot_rag_be.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Utility class for validating file types allowed for document upload.
 */
@Component
@Slf4j
public class FileTypeValidator {

    // Allowed MIME types for document upload
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            // PDF
            "application/pdf",
            
            // Microsoft Office
            "application/msword",                                                          // .doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",   // .docx
            "application/vnd.ms-excel",                                                    // .xls
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",         // .xlsx
            "application/vnd.ms-powerpoint",                                               // .ppt
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .pptx
            
            // Text files
            "text/plain",                    // .txt
            "text/markdown",                 // .md
            "text/csv",                      // .csv
            
            // Images (for OCR if needed)
            "image/png",
            "image/jpeg",
            "image/jpg"
    );

    // Allowed file extensions
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf",
            ".doc", ".docx",
            ".xls", ".xlsx",
            ".ppt", ".pptx",
            ".txt", ".md", ".csv",
            ".png", ".jpg", ".jpeg"
    );

    // Maximum file size: 50MB
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    /**
     * Validate if file type is allowed.
     */
    public boolean isAllowedMimeType(String mimeType) {
        return ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase());
    }

    /**
     * Validate if file extension is allowed.
     */
    public boolean isAllowedExtension(String filename) {
        String lowercaseFilename = filename.toLowerCase();
        return ALLOWED_EXTENSIONS.stream()
                .anyMatch(lowercaseFilename::endsWith);
    }

    /**
     * Validate file size.
     */
    public boolean isValidSize(long sizeInBytes) {
        return sizeInBytes > 0 && sizeInBytes <= MAX_FILE_SIZE;
    }

    /**
     * Get user-friendly file size limit message.
     */
    public String getMaxSizeMessage() {
        return "50MB";
    }

    /**
     * Get list of allowed extensions for display.
     */
    public String getAllowedExtensionsMessage() {
        return String.join(", ", ALLOWED_EXTENSIONS);
    }

    /**
     * Comprehensive validation with detailed error message.
     */
    public ValidationResult validate(String filename, String mimeType, long sizeInBytes) {
        // Check file extension
        if (!isAllowedExtension(filename)) {
            return new ValidationResult(false, 
                "File type not allowed. Allowed types: " + getAllowedExtensionsMessage());
        }

        // Check MIME type
        if (!isAllowedMimeType(mimeType)) {
            return new ValidationResult(false, 
                "MIME type '" + mimeType + "' not allowed");
        }

        // Check file size
        if (!isValidSize(sizeInBytes)) {
            if (sizeInBytes <= 0) {
                return new ValidationResult(false, "File is empty");
            }
            return new ValidationResult(false, 
                "File size exceeds maximum limit of " + getMaxSizeMessage());
        }

        return new ValidationResult(true, "Valid");
    }

    /**
     * Result of file validation.
     */
    public record ValidationResult(boolean valid, String message) {
        public boolean isValid() {
            return valid;
        }
    }

    /**
     * Determine document category based on file extension.
     */
    public String categorizeFile(String filename) {
        String lower = filename.toLowerCase();
        
        if (lower.endsWith(".pdf")) return "PDF Document";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "Word Document";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "Excel Spreadsheet";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "PowerPoint Presentation";
        if (lower.endsWith(".txt") || lower.endsWith(".md")) return "Text Document";
        if (lower.endsWith(".csv")) return "CSV Data";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "Image";
        
        return "Unknown";
    }
}

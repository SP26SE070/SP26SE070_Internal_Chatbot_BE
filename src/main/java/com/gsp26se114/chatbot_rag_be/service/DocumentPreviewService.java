package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.exception.PreviewRenderException;
import com.gsp26se114.chatbot_rag_be.exception.PreviewUnsupportedFormatException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class DocumentPreviewService {

    private static final String PREVIEW_MODE_NATIVE = "native";
    private static final String PREVIEW_MODE_RENDERED = "rendered";

    private static final Set<String> OFFICE_MIME_TYPES = Set.of(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private static final Set<String> TEXT_MIME_TYPES = Set.of(
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/json",
            "application/xml",
            "text/xml"
    );

    private static final Set<String> OFFICE_EXTENSIONS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    private static final List<String> FONT_PATH_CANDIDATES = List.of(
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/tahoma.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
    );

    private static final List<String> SOFFICE_PATH_CANDIDATES = List.of(
            "C:/Program Files/LibreOffice/program/soffice.exe",
            "C:/Program Files (x86)/LibreOffice/program/soffice.exe",
            "/usr/bin/libreoffice",
            "/usr/bin/soffice"
    );

    private static final String OFFICE_COM_SCRIPT = """
            param(
                [Parameter(Mandatory=$true)][string]$InputPath,
                [Parameter(Mandatory=$true)][string]$OutputPath,
                [Parameter(Mandatory=$true)][ValidateSet("word","excel","powerpoint")][string]$App
            )
            $ErrorActionPreference = "Stop"

            switch ($App) {
                "word" {
                    $office = New-Object -ComObject Word.Application
                    $office.Visible = $false
                    $office.DisplayAlerts = 0
                    try {
                        $doc = $office.Documents.Open($InputPath, $false, $true)
                        try {
                            $wdFormatPDF = 17
                            $doc.SaveAs([ref]$OutputPath, [ref]$wdFormatPDF)
                        } finally {
                            $doc.Close([ref]$false)
                        }
                    } finally {
                        $office.Quit()
                    }
                }
                "excel" {
                    $office = New-Object -ComObject Excel.Application
                    $office.Visible = $false
                    $office.DisplayAlerts = $false
                    try {
                        $workbook = $office.Workbooks.Open($InputPath, 0, $true)
                        try {
                            $xlTypePDF = 0
                            $workbook.ExportAsFixedFormat($xlTypePDF, $OutputPath)
                        } finally {
                            $workbook.Close($false)
                        }
                    } finally {
                        $office.Quit()
                    }
                }
                "powerpoint" {
                    $office = New-Object -ComObject PowerPoint.Application
                    try {
                        $presentation = $office.Presentations.Open($InputPath, $true, $false, $false)
                        try {
                            $ppSaveAsPDF = 32
                            $presentation.SaveAs($OutputPath, $ppSaveAsPDF)
                        } finally {
                            $presentation.Close()
                        }
                    } finally {
                        $office.Quit()
                    }
                }
            }
            """;

    private final ConcurrentMap<String, CachedPreview> previewCache = new ConcurrentHashMap<>();

    @Value("${preview.render-timeout-ms:15000}")
    private long renderTimeoutMs;

    @Value("${preview.cache-ttl-seconds:600}")
    private long cacheTtlSeconds;

    @Value("${preview.office-native-timeout-ms:45000}")
    private long officeNativeTimeoutMs;

    @Value("${preview.office.soffice-path:}")
    private String configuredSofficePath;

    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(2, new PreviewRenderThreadFactory());

    public PreviewResult buildPreview(
            String cacheKey,
            String originalFileName,
            String sourceContentType,
            byte[] sourceBytes,
            String ifNoneMatch
    ) {
        String normalizedSourceType = normalizeMimeType(sourceContentType);
        String safeOriginalFileName = normalizeFileName(originalFileName);
        String sourceDigest = sha256Hex(sourceBytes);

        Instant now = Instant.now();
        CachedPreview cached = previewCache.get(cacheKey);
        if (cached != null && cached.isExpired(now)) {
            previewCache.remove(cacheKey, cached);
            cached = null;
        }

        if (cached != null && cached.sourceDigest().equals(sourceDigest)) {
            if (matchesIfNoneMatch(ifNoneMatch, cached.etag())) {
                return PreviewResult.notModified(cached.etag(), cached.previewMode(), cached.sourceContentType());
            }
            return cached.toResult();
        }

        byte[] outputBytes;
        String outputContentType;
        String outputFileName;
        String previewMode;

        if (MediaType.APPLICATION_PDF_VALUE.equals(normalizedSourceType)) {
            outputBytes = sourceBytes;
            outputContentType = MediaType.APPLICATION_PDF_VALUE;
            outputFileName = ensurePdfExtension(safeOriginalFileName);
            previewMode = PREVIEW_MODE_NATIVE;
        } else if (isTextPreviewType(normalizedSourceType)) {
            outputBytes = sourceBytes;
            outputContentType = normalizedSourceType;
            outputFileName = safeOriginalFileName;
            previewMode = PREVIEW_MODE_NATIVE;
        } else if (isOfficePreviewType(normalizedSourceType, safeOriginalFileName)) {
            outputBytes = renderOfficeToPdfWithTimeout(sourceBytes, safeOriginalFileName, normalizedSourceType);
            outputContentType = MediaType.APPLICATION_PDF_VALUE;
            outputFileName = toPdfFileName(safeOriginalFileName);
            previewMode = PREVIEW_MODE_RENDERED;
        } else {
            throw new PreviewUnsupportedFormatException(
                    "Preview mode is not supported for content type: " + normalizedSourceType
            );
        }

        String etag = "\"" + sha256Hex(outputBytes) + "\"";
        CachedPreview updatedEntry = new CachedPreview(
                sourceDigest,
                outputBytes,
                outputContentType,
                outputFileName,
                previewMode,
                normalizedSourceType,
                etag,
                now.plusSeconds(Math.max(60L, cacheTtlSeconds))
        );
        previewCache.put(cacheKey, updatedEntry);

        if (matchesIfNoneMatch(ifNoneMatch, etag)) {
            return PreviewResult.notModified(etag, previewMode, normalizedSourceType);
        }

        return updatedEntry.toResult();
    }

    private byte[] renderOfficeToPdfWithTimeout(byte[] sourceBytes, String fileName, String sourceContentType) {
        Future<byte[]> renderTask = renderExecutor.submit(() -> {
            try {
                return renderOfficeToPdfWithNativeConverter(sourceBytes, fileName, sourceContentType);
            } catch (PreviewRenderException nativeError) {
                log.warn("Native office conversion failed for {}: {}. Falling back to text-based preview.",
                        fileName, nativeError.getMessage());

                String extractedText = extractOfficeText(sourceBytes);
                if (extractedText.isBlank()) {
                    extractedText = "No previewable text was extracted from this document.";
                }
                return renderTextToPdf(extractedText, fileName);
            }
        });

        long timeout = Math.max(Math.max(1000L, renderTimeoutMs), Math.max(2000L, officeNativeTimeoutMs));
        try {
            return renderTask.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            renderTask.cancel(true);
            throw new PreviewRenderException("Preview render timed out for " + sourceContentType + " after " + timeout + "ms", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PreviewRenderException("Preview rendering interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw new PreviewRenderException("Preview rendering failed", cause);
        }
    }

    private byte[] renderOfficeToPdfWithNativeConverter(byte[] sourceBytes, String fileName, String sourceContentType) {
        String extension = extensionOf(fileName);
        if (extension.isBlank()) {
            extension = inferExtensionFromContentType(sourceContentType);
        }
        if (extension.isBlank()) {
            throw new PreviewRenderException("Cannot determine Office file extension for native conversion");
        }

        PreviewRenderException firstError = null;

        if (isWindowsHost()) {
            try {
                return renderWithMicrosoftOfficeCom(sourceBytes, extension);
            } catch (PreviewRenderException ex) {
                firstError = ex;
            }
        }

        try {
            return renderWithSoffice(sourceBytes, extension);
        } catch (PreviewRenderException ex) {
            if (firstError != null) {
                throw new PreviewRenderException(firstError.getMessage() + " | " + ex.getMessage(), ex);
            }
            throw ex;
        }
    }

    private byte[] renderWithMicrosoftOfficeCom(byte[] sourceBytes, String extension) {
        String app = officeAppForExtension(extension);
        if (app == null) {
            throw new PreviewRenderException("Microsoft Office converter does not support extension: " + extension);
        }

        Path tempDir = createTempDir("preview-office-com-");
        try {
            Path inputFile = tempDir.resolve("source." + extension);
            Path outputFile = tempDir.resolve("source.pdf");
            Path scriptFile = tempDir.resolve("office-to-pdf.ps1");

            Files.write(inputFile, sourceBytes);
            Files.writeString(scriptFile, OFFICE_COM_SCRIPT, StandardCharsets.UTF_8);

            List<String> command = List.of(
                    "powershell",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptFile.toString(),
                    "-InputPath",
                    inputFile.toString(),
                    "-OutputPath",
                    outputFile.toString(),
                    "-App",
                    app
            );

            runExternalConversion(command, outputFile, Math.max(2000L, officeNativeTimeoutMs),
                    "Microsoft Office COM conversion failed");
            return Files.readAllBytes(outputFile);
        } catch (IOException ex) {
            throw new PreviewRenderException("Failed during Microsoft Office COM conversion", ex);
        } finally {
            deleteRecursivelyQuietly(tempDir);
        }
    }

    private byte[] renderWithSoffice(byte[] sourceBytes, String extension) {
        String sofficeExecutable = resolveSofficeExecutable();
        if (sofficeExecutable == null) {
            throw new PreviewRenderException("LibreOffice (soffice) not found for native Office conversion");
        }

        Path tempDir = createTempDir("preview-soffice-");
        try {
            Path inputFile = tempDir.resolve("source." + extension);
            Path outputFile = tempDir.resolve("source.pdf");
            Files.write(inputFile, sourceBytes);

            List<String> command = List.of(
                    sofficeExecutable,
                    "--headless",
                    "--invisible",
                    "--nodefault",
                    "--nolockcheck",
                    "--nologo",
                    "--norestore",
                    "--convert-to",
                    "pdf",
                    "--outdir",
                    tempDir.toString(),
                    inputFile.toString()
            );

            runExternalConversion(command, outputFile, Math.max(2000L, officeNativeTimeoutMs),
                    "LibreOffice conversion failed");
            return Files.readAllBytes(outputFile);
        } catch (IOException ex) {
            throw new PreviewRenderException("Failed during LibreOffice conversion", ex);
        } finally {
            deleteRecursivelyQuietly(tempDir);
        }
    }

    private void runExternalConversion(List<String> command, Path expectedPdf, long timeoutMs, String errorPrefix) {
        Process process;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = processBuilder.start();
        } catch (IOException ex) {
            throw new PreviewRenderException(errorPrefix + ": cannot start process", ex);
        }

        try {
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new PreviewRenderException(errorPrefix + ": timed out after " + timeoutMs + "ms");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new PreviewRenderException(errorPrefix + ": process exited with code " + exitCode);
            }
            if (!Files.exists(expectedPdf) || Files.size(expectedPdf) == 0L) {
                throw new PreviewRenderException(errorPrefix + ": output PDF was not generated");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PreviewRenderException(errorPrefix + ": interrupted", ex);
        } catch (IOException ex) {
            throw new PreviewRenderException(errorPrefix + ": failed to read generated PDF", ex);
        }
    }

    private String inferExtensionFromContentType(String sourceContentType) {
        String normalized = normalizeMimeType(sourceContentType);
        return switch (normalized) {
            case "application/msword" -> "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.ms-excel" -> "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.ms-powerpoint" -> "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
            default -> "";
        };
    }

    private String officeAppForExtension(String extension) {
        String normalized = extension.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "doc", "docx" -> "word";
            case "xls", "xlsx" -> "excel";
            case "ppt", "pptx" -> "powerpoint";
            default -> null;
        };
    }

    private String resolveSofficeExecutable() {
        if (configuredSofficePath != null && !configuredSofficePath.isBlank()) {
            Path configured = Path.of(configuredSofficePath);
            if (Files.exists(configured)) {
                return configured.toString();
            }
        }

        for (String candidate : SOFFICE_PATH_CANDIDATES) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                return path.toString();
            }
        }

        return null;
    }

    private boolean isWindowsHost() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private Path createTempDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException ex) {
            throw new PreviewRenderException("Failed to create temporary directory for preview conversion", ex);
        }
    }

    private void deleteRecursivelyQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }

        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Best-effort cleanup only.
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private String extractOfficeText(byte[] sourceBytes) {
        try (InputStream inputStream = new ByteArrayInputStream(sourceBytes);
             POITextExtractor extractor = ExtractorFactory.createExtractor(inputStream)) {
            String text = extractor.getText();
            return text == null ? "" : normalizeExtractedText(text);
        } catch (Exception ex) {
            throw new PreviewRenderException("Failed to parse Office document", ex);
        }
    }

    private byte[] renderTextToPdf(String text, String fileName) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDFont font = loadPreferredFont(document);
            float fontSize = 10.5f;
            float margin = 42f;
            float leading = 15f;

            List<String> lines = wrapTextIntoLines(text, font, fontSize, PDRectangle.A4.getWidth() - (2 * margin));
            if (lines.isEmpty()) {
                lines = List.of("No previewable text was extracted from: " + fileName);
            }

            int lineIndex = 0;
            while (lineIndex < lines.size()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                float y = page.getMediaBox().getHeight() - margin;
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(font, fontSize);
                    contentStream.newLineAtOffset(margin, y);

                    while (lineIndex < lines.size()) {
                        if ((y - leading) < margin) {
                            break;
                        }
                        String line = sanitizePdfLine(lines.get(lineIndex));
                        contentStream.showText(line);
                        contentStream.newLineAtOffset(0, -leading);
                        y -= leading;
                        lineIndex++;
                    }

                    contentStream.endText();
                }
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new PreviewRenderException("Failed to generate preview PDF", ex);
        }
    }

    private List<String> wrapTextIntoLines(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] paragraphs = text.split("\\n", -1);

        for (String paragraph : paragraphs) {
            String normalizedParagraph = paragraph.replace('\t', ' ');
            if (normalizedParagraph.isBlank()) {
                lines.add("");
                continue;
            }

            StringBuilder currentLine = new StringBuilder();
            for (String token : normalizedParagraph.split(" ")) {
                if (token.isEmpty()) {
                    continue;
                }

                String candidate = currentLine.isEmpty() ? token : currentLine + " " + token;
                if (computeTextWidth(candidate, font, fontSize) <= maxWidth) {
                    currentLine.setLength(0);
                    currentLine.append(candidate);
                    continue;
                }

                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                }

                if (computeTextWidth(token, font, fontSize) <= maxWidth) {
                    currentLine.append(token);
                    continue;
                }

                lines.addAll(splitLongToken(token, font, fontSize, maxWidth));
            }

            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString());
            }
        }

        return lines;
    }

    private List<String> splitLongToken(String token, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < token.length(); i++) {
            char nextChar = token.charAt(i);
            String candidate = current.toString() + nextChar;
            if (!current.isEmpty() && computeTextWidth(candidate, font, fontSize) > maxWidth) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            current.append(nextChar);
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
    }

    private float computeTextWidth(String text, PDFont font, float fontSize) throws IOException {
        return (font.getStringWidth(text) / 1000f) * fontSize;
    }

    private PDFont loadPreferredFont(PDDocument document) {
        List<String> candidates = new ArrayList<>();
        String systemFont = System.getProperty("preview.pdf.font-path");
        String envFont = System.getenv("PREVIEW_PDF_FONT_PATH");
        if (systemFont != null && !systemFont.isBlank()) {
            candidates.add(systemFont);
        }
        if (envFont != null && !envFont.isBlank()) {
            candidates.add(envFont);
        }
        candidates.addAll(FONT_PATH_CANDIDATES);

        for (String candidate : candidates) {
            try {
                Path path = Path.of(candidate);
                if (!Files.exists(path)) {
                    continue;
                }
                try (InputStream fontInput = Files.newInputStream(path)) {
                    return PDType0Font.load(document, fontInput, true);
                }
            } catch (Exception ex) {
                log.debug("Could not load font from {}: {}", candidate, ex.getMessage());
            }
        }

        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private String sanitizePdfLine(String line) {
        StringBuilder sanitized = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c >= 32 || c == '\t') {
                sanitized.append(c);
            }
        }
        return sanitized.toString();
    }

    private String normalizeExtractedText(String text) {
        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u0000", "");
    }

    private boolean isTextPreviewType(String normalizedMimeType) {
        if (normalizedMimeType.startsWith("text/")) {
            return true;
        }
        if (TEXT_MIME_TYPES.contains(normalizedMimeType)) {
            return true;
        }
        return normalizedMimeType.startsWith("application/")
                && (normalizedMimeType.endsWith("+json") || normalizedMimeType.endsWith("+xml"));
    }

    private boolean isOfficePreviewType(String normalizedMimeType, String fileName) {
        if (OFFICE_MIME_TYPES.contains(normalizedMimeType)) {
            return true;
        }
        String extension = extensionOf(fileName);
        return OFFICE_EXTENSIONS.contains(extension);
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String normalized = mimeType.trim();
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex > 0) {
            normalized = normalized.substring(0, semicolonIndex);
        }

        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document";
        }
        return fileName;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String toPdfFileName(String sourceFileName) {
        int dot = sourceFileName.lastIndexOf('.');
        if (dot > 0) {
            return sourceFileName.substring(0, dot) + ".pdf";
        }
        return sourceFileName + ".pdf";
    }

    private String ensurePdfExtension(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".pdf") ? fileName : toPdfFileName(fileName);
    }

    private boolean matchesIfNoneMatch(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }

        List<String> candidates = Arrays.stream(ifNoneMatch.split(","))
                .map(String::trim)
                .toList();

        for (String candidate : candidates) {
            if ("*".equals(candidate)) {
                return true;
            }
            if (stripWeakTag(candidate).equals(stripWeakTag(etag))) {
                return true;
            }
        }
        return false;
    }

    private String stripWeakTag(String etag) {
        String normalized = etag.trim();
        if (normalized.startsWith("W/")) {
            return normalized.substring(2).trim();
        }
        return normalized;
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String part = Integer.toHexString(0xff & b);
                if (part.length() == 1) {
                    hex.append('0');
                }
                hex.append(part);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    @PreDestroy
    public void shutdownRenderer() {
        renderExecutor.shutdownNow();
    }

    public record PreviewResult(
            byte[] content,
            String contentType,
            String fileName,
            String previewMode,
            String sourceContentType,
            String etag,
            boolean notModified
    ) {
        public static PreviewResult notModified(String etag, String previewMode, String sourceContentType) {
            return new PreviewResult(
                    new byte[0],
                    MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    "",
                    previewMode,
                    sourceContentType,
                    etag,
                    true
            );
        }
    }

    private record CachedPreview(
            String sourceDigest,
            byte[] content,
            String contentType,
            String fileName,
            String previewMode,
            String sourceContentType,
            String etag,
            Instant expiresAt
    ) {
        boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }

        PreviewResult toResult() {
            return new PreviewResult(
                    content,
                    contentType,
                    fileName,
                    previewMode,
                    sourceContentType,
                    etag,
                    false
            );
        }
    }

    private static class PreviewRenderThreadFactory implements ThreadFactory {
        private int index = 1;

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "preview-render-" + index++);
            thread.setDaemon(true);
            return thread;
        }
    }
}

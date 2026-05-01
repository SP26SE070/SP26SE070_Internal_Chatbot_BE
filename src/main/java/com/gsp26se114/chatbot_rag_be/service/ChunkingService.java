package com.gsp26se114.chatbot_rag_be.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Smart chunking theo loại file (ký tự, max 1000 / min 100 / overlap 100).
 * PDF: theo trang; trang ít chữ (nhiều ảnh) → 1 chunk/trang; trang text → đoạn rồi câu.
 * DOCX: theo marker heading từ {@link TextExtractorService}.
 * PPTX: theo slide (form-feed).
 * TXT/MD: đoạn + giữ khối code markdown nguyên khi có thể.
 * XLSX/CSV: gom dòng, lặp lại header mỗi chunk.
 */
@Service
@Slf4j
public class ChunkingService {

    @Value("${rag.chunk-max-chars:1000}")
    private int chunkMaxChars;

    @Value("${rag.chunk-min-chars:100}")
    private int chunkMinChars;

    @Value("${rag.chunk-overlap-chars:100}")
    private int chunkOverlapChars;

    @Value("${rag.spreadsheet-rows-per-chunk:18}")
    private int spreadsheetRowsPerChunk;

    /** Trang PDF có ít hơn số ký tự này (sau strip) → coi là trang “nhiều ảnh”, một chunk cả trang. */
    @Value("${rag.pdf-sparse-page-char-threshold:80}")
    private int pdfSparsePageCharThreshold;

    private static final Pattern CODE_FENCE = Pattern.compile("```[\\s\\S]*?```");

    @Deprecated
    public List<String> splitText(String text) {
        return splitPlainOrMarkdown(text);
    }

    public List<String> splitText(String text, String fileType, String originalFileName) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String mime = TextExtractorService.resolveEffectiveContentType(
                fileType == null ? "" : fileType,
                originalFileName
        ).toLowerCase(Locale.ROOT);

        List<String> chunks = switch (mime) {
            case "application/pdf" -> splitPdf(text);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> splitDocxByHeadings(text);
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> splitPptxBySlides(text);
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv" -> splitSpreadsheet(text);
            case "text/plain", "text/markdown" -> splitPlainOrMarkdown(text);
            default -> splitPlainOrMarkdown(text);
        };

        List<String> merged = mergeTinyTrailingChunks(chunks);
        log.debug("Chunking done: {} chunks (mime={}, max={}, min={}, overlap={}, rows={})",
                merged.size(), mime, chunkMaxChars, chunkMinChars, chunkOverlapChars, spreadsheetRowsPerChunk);
        return merged;
    }

    private List<String> splitPdf(String text) {
        String n = normalizeNewlines(text);
        String[] pages = n.split("\\f+");
        List<String> out = new ArrayList<>();
        for (String page : pages) {
            String p = page.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() < pdfSparsePageCharThreshold) {
                out.add(p);
            } else {
                out.addAll(splitByParagraphsThenSentences(p, null));
            }
        }
        return out.isEmpty() ? splitByParagraphsThenSentences(n.replace('\f', '\n'), null) : out;
    }

    private List<String> splitDocxByHeadings(String text) {
        String n = normalizeNewlines(text);
        if (!n.contains("<<<DOCX_H:")) {
            return splitByParagraphsThenSentences(n, null);
        }
        List<String> out = new ArrayList<>();
        for (String part : n.split("(?=<<<DOCX_H:)")) {
            String p = part.strip();
            if (p.isEmpty()) {
                continue;
            }
            String prefix = null;
            String body = p;
            int nl = p.indexOf('\n');
            if (p.startsWith("<<<DOCX_H:") && nl > 0) {
                prefix = p.substring(0, nl).strip();
                body = p.substring(nl + 1).strip();
            }
            out.addAll(splitByParagraphsThenSentences(body.isEmpty() ? p : body, prefix));
        }
        return out;
    }

    private List<String> splitPptxBySlides(String text) {
        String n = normalizeNewlines(text);
        List<String> out = new ArrayList<>();
        for (String slide : n.split("\\f+")) {
            String s = slide.strip();
            if (s.isEmpty()) {
                continue;
            }
            if (s.length() <= chunkMaxChars) {
                out.add(s);
            } else {
                int br = s.indexOf('\n');
                int cut = br > 0 ? Math.min(br, 120) : Math.min(s.length(), 120);
                String prefix = s.startsWith("Slide ") ? s.substring(0, cut) : "";
                out.addAll(splitLongTextWithSentenceBoundaries(s, prefix));
            }
        }
        return out.isEmpty() ? splitByParagraphsThenSentences(n.replace('\f', '\n'), null) : out;
    }

    private List<String> splitPlainOrMarkdown(String text) {
        String n = normalizeNewlines(text);
        if (!n.contains("```")) {
            return splitByParagraphsThenSentences(n, null);
        }
        List<String> units = new ArrayList<>();
        Matcher fm = CODE_FENCE.matcher(n);
        int last = 0;
        while (fm.find()) {
            if (fm.start() > last) {
                units.add(n.substring(last, fm.start()).strip());
            }
            units.add(fm.group().strip());
            last = fm.end();
        }
        if (last < n.length()) {
            units.add(n.substring(last).strip());
        }
        List<String> out = new ArrayList<>();
        for (String u : units) {
            if (u.isEmpty()) {
                continue;
            }
            if (u.startsWith("```")) {
                out.addAll(splitCodeBlockUnit(u));
            } else {
                out.addAll(splitByParagraphsThenSentences(u, null));
            }
        }
        return out;
    }

    private List<String> splitCodeBlockUnit(String codeBlock) {
        if (codeBlock.length() <= chunkMaxChars) {
            return List.of(codeBlock);
        }
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < codeBlock.length()) {
            int end = Math.min(i + chunkMaxChars, codeBlock.length());
            int cut = end;
            if (end < codeBlock.length()) {
                int nl = codeBlock.lastIndexOf('\n', end);
                if (nl > i + chunkMinChars) {
                    cut = nl;
                }
            }
            parts.add(codeBlock.substring(i, cut).strip());
            i = cut;
            if (i < codeBlock.length() && codeBlock.charAt(i) == '\n') {
                i++;
            }
        }
        return parts;
    }

    private List<String> splitSpreadsheet(String text) {
        String n = normalizeNewlines(text);
        String[] lines = n.split("\n", -1);
        List<String> chunks = new ArrayList<>();
        final String[] headerHolder = new String[1];
        List<String> rowBuffer = new ArrayList<>();
        Runnable flush = () -> {
            if (rowBuffer.isEmpty()) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            String header = headerHolder[0];
            if (header != null && !header.isBlank()) {
                sb.append("Headers: ").append(header.replace("\t", " | ")).append('\n');
            }
            for (int r = 0; r < rowBuffer.size(); r++) {
                sb.append("Row ").append(r + 1).append(": ").append(rowBuffer.get(r).replace("\t", " | ")).append('\n');
            }
            chunks.add(sb.toString().strip());
            rowBuffer.clear();
        };

        for (String raw : lines) {
            String line = raw;
            if (line.stripLeading().startsWith("### SHEET:")) {
                flush.run();
                headerHolder[0] = null;
                chunks.add(line.strip());
                continue;
            }
            if (line.isBlank() && rowBuffer.isEmpty() && headerHolder[0] == null) {
                continue;
            }
            if (headerHolder[0] == null) {
                headerHolder[0] = line.strip();
                continue;
            }
            rowBuffer.add(line);
            if (rowBuffer.size() >= spreadsheetRowsPerChunk) {
                flush.run();
            }
        }
        flush.run();
        return chunks.stream().filter(s -> !s.isBlank()).toList();
    }

    /**
     * Gom đoạn (\\n\\n+); prefixHeading gắn lại đầu mỗi chunk khi chia nhỏ (DOCX).
     */
    private List<String> splitByParagraphsThenSentences(String text, String prefixHeading) {
        String n = normalizeNewlines(text);
        String[] paras = n.split("\\n{2,}");
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String prefix = prefixHeading != null ? prefixHeading.strip() : null;

        for (String para : paras) {
            String p = para.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() > chunkMaxChars) {
                if (cur.length() > 0) {
                    out.add(finishChunk(cur, prefix));
                    cur = new StringBuilder();
                    appendOverlap(cur, out.get(out.size() - 1));
                }
                out.addAll(splitLongTextWithSentenceBoundaries(p, prefix));
                continue;
            }
            String glue = cur.length() > 0 ? "\n\n" : "";
            if (cur.length() + glue.length() + p.length() > chunkMaxChars && cur.length() > 0) {
                out.add(finishChunk(cur, prefix));
                cur = new StringBuilder();
                appendOverlap(cur, out.get(out.size() - 1));
            }
            if (cur.length() > 0) {
                cur.append("\n\n");
            }
            cur.append(p);
        }
        if (cur.length() > 0) {
            out.add(finishChunk(cur, prefix));
        }
        return out;
    }

    private String finishChunk(StringBuilder cur, String prefix) {
        String body = cur.toString().strip();
        cur.setLength(0);
        if (prefix != null && !prefix.isBlank() && !body.startsWith(prefix)) {
            return prefix + "\n\n" + body;
        }
        return body;
    }

    private List<String> splitLongTextWithSentenceBoundaries(String text, String prefix) {
        List<String> sentences = new ArrayList<>();
        for (String part : text.split("(?<=[.!?。…])\\s+")) {
            String s = part.strip();
            if (!s.isEmpty()) {
                sentences.add(s);
            }
        }
        if (sentences.isEmpty()) {
            return splitByCharChunks(text, prefix);
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String sent : sentences) {
            if (sent.length() > chunkMaxChars) {
                if (cur.length() > 0) {
                    chunks.add(applyPrefix(prefix, cur.toString().strip()));
                    cur = new StringBuilder();
                    appendOverlap(cur, chunks.get(chunks.size() - 1));
                }
                chunks.addAll(splitByCharChunks(sent, prefix));
                continue;
            }
            String glue = cur.length() > 0 ? " " : "";
            if (cur.length() + glue.length() + sent.length() > chunkMaxChars && cur.length() > 0) {
                chunks.add(applyPrefix(prefix, cur.toString().strip()));
                cur = new StringBuilder();
                appendOverlap(cur, chunks.get(chunks.size() - 1));
            }
            if (cur.length() > 0) {
                cur.append(' ');
            }
            cur.append(sent);
        }
        if (cur.length() > 0) {
            chunks.add(applyPrefix(prefix, cur.toString().strip()));
        }
        return chunks;
    }

    private List<String> splitByCharChunks(String text, String prefix) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(i + chunkMaxChars, text.length());
            if (end < text.length()) {
                int sp = text.lastIndexOf(' ', end);
                if (sp > i + chunkMinChars) {
                    end = sp;
                }
            }
            out.add(applyPrefix(prefix, text.substring(i, end).strip()));
            i = end;
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
        }
        return out;
    }

    private static String applyPrefix(String prefix, String body) {
        if (prefix == null || prefix.isBlank()) {
            return body;
        }
        if (body.startsWith(prefix)) {
            return body;
        }
        return prefix + "\n\n" + body;
    }

    private void appendOverlap(StringBuilder nextChunk, String previousChunk) {
        if (previousChunk == null || previousChunk.isEmpty() || chunkOverlapChars <= 0) {
            return;
        }
        int take = Math.min(chunkOverlapChars, previousChunk.length());
        String tail = previousChunk.substring(previousChunk.length() - take);
        int sp = tail.indexOf(' ');
        if (sp > 0 && sp < tail.length() - 20) {
            tail = tail.substring(sp + 1);
        }
        nextChunk.append(tail).append('\n');
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** Gộp chunk cuối quá ngắn vào chunk trước nếu tổng vẫn <= max. */
    private List<String> mergeTinyTrailingChunks(List<String> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }
        List<String> out = new ArrayList<>();
        for (String c : chunks) {
            if (c == null || c.isBlank()) {
                continue;
            }
            if (!out.isEmpty() && c.length() < chunkMinChars) {
                String prev = out.get(out.size() - 1);
                if (prev.length() + c.length() + 2 <= chunkMaxChars) {
                    out.set(out.size() - 1, prev + "\n\n" + c);
                    continue;
                }
            }
            out.add(c);
        }
        return out;
    }
}

package com.gsp26se114.chatbot_rag_be.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service để chia text thành chunks nhỏ
 * Sử dụng sliding window với overlap
 */
@Service
@Slf4j
public class ChunkingService {

    @Value("${rag.chunk-size:500}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:50}")
    private int chunkOverlap;

    /**
     * Split text thành chunks với overlap
     * 
     * @param text Full text content
     * @return List of text chunks
     */
    public List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        
        // Split by sentences - supports Vietnamese punctuation
        String[] sentences = text.split("(?<=[.!?。…])\\s+|(?<=\\n)\\s*");
        
        StringBuilder currentChunk = new StringBuilder();
        int currentLength = 0;

        for (String sentence : sentences) {
            int sentenceLength = estimateTokenCount(sentence);
            
            // Nếu sentence quá dài, chia nhỏ thêm
            if (sentenceLength > chunkSize) {
                // Flush current chunk nếu có
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                    currentLength = 0;
                }
                
                // Split long sentence
                chunks.addAll(splitLongSentence(sentence));
                continue;
            }
            
            // Nếu thêm sentence này vượt chunkSize
            if (currentLength + sentenceLength > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                
                // Overlap: giữ lại một phần cuối chunk cũ
                String overlap = getOverlapText(currentChunk.toString());
                currentChunk = new StringBuilder(overlap);
                currentLength = estimateTokenCount(overlap);
            }
            
            currentChunk.append(sentence).append(" ");
            currentLength += sentenceLength;
        }

        // Add last chunk
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        log.debug("Split text into {} chunks (size={}, overlap={})", 
                 chunks.size(), chunkSize, chunkOverlap);
        
        return chunks;
    }

    /**
     * Split sentence quá dài theo words
     */
    private List<String> splitLongSentence(String sentence) {
        List<String> chunks = new ArrayList<>();
        String[] words = sentence.split("\\s+");
        
        StringBuilder chunk = new StringBuilder();
        int tokenCount = 0;
        
        for (String word : words) {
            int wordTokens = estimateTokenCount(word);
            
            if (tokenCount + wordTokens > chunkSize && chunk.length() > 0) {
                chunks.add(chunk.toString().trim());
                chunk = new StringBuilder();
                tokenCount = 0;
            }
            
            chunk.append(word).append(" ");
            tokenCount += wordTokens;
        }
        
        if (chunk.length() > 0) {
            chunks.add(chunk.toString().trim());
        }
        
        return chunks;
    }

    /**
     * Lấy phần cuối của text để overlap
     */
    private String getOverlapText(String text) {
        String[] words = text.split("\\s+");
        int overlapWords = Math.min(chunkOverlap / 4, words.length); // ~4 chars per token
        
        if (overlapWords == 0) {
            return "";
        }
        
        StringBuilder overlap = new StringBuilder();
        for (int i = words.length - overlapWords; i < words.length; i++) {
            overlap.append(words[i]).append(" ");
        }
        
        return overlap.toString();
    }

    /**
     * Estimate token count - Vietnamese text uses ~2-3 chars per token
     * ASCII text uses ~4 chars per token
     */
    private int estimateTokenCount(String text) {
        if (text == null) return 0;
        long nonAscii = text.chars().filter(c -> c > 127).count();
        long ascii = text.length() - nonAscii;
        // Vietnamese chars ≈ 2 chars/token, ASCII ≈ 4 chars/token
        return (int) (nonAscii / 2 + ascii / 4);
    }
}

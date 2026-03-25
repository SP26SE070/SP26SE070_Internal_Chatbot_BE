package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for chatbot answer with source documents
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {
    
    private String answer;

    /**
     * ID of the stored assistant message (for rating purposes).
     */
    private UUID messageId;

    private String conversationId;
    
    private List<SourceDocument> sources;
    
    private Long responseTimeMs;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SourceDocument {
        private String documentId;
        private String fileName;
        private String chunkContent;
        private Integer chunkIndex;
        private Double relevanceScore;
    }
}

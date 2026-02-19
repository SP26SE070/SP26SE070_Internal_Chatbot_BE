package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for chatbot query with RAG
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    @NotBlank(message = "Message cannot be empty")
    private String message;
    
    /**
     * Optional conversation ID for multi-turn chat
     */
    private String conversationId;
    
    /**
     * Number of chunks to retrieve for context (default: 5)
     */
    private Integer topK = 5;
}

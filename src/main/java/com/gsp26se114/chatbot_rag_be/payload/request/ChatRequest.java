package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

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
     * Number of chunks to retrieve for context (default: 7)
     */
    private Integer topK = 7;

    /** Optional metadata filter theo category. */
    private UUID categoryId;

    /** Optional metadata filter theo tags. */
    private List<UUID> tagIds;

    /**
     * Khi FE đang hỏi trong ngữ cảnh một tài liệu cụ thể (ví dụ mở từ Document Dashboard):
     * BE kiểm tra quyền đọc trước khi RAG; không đủ quyền → trả đúng một câu từ chối, không gọi LLM trên nội dung tài liệu.
     */
    private UUID targetDocumentId;
}

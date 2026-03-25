package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponse {
    private UUID messageId;
    private String conversationId;
    private String role;          // USER or ASSISTANT
    private String content;
    private List<Object> sourceChunks;  // JSONB source chunks (ASSISTANT only)
    private Integer rating;       // 1-5 (ASSISTANT only)
    private String feedbackText;
    private LocalDateTime createdAt;
}

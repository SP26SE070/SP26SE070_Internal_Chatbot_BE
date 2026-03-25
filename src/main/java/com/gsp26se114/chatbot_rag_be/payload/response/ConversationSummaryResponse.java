package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationSummaryResponse {
    private UUID conversationId;
    private String title;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime lastMessageAt;
    private Integer totalMessages;
    private Integer totalTokensUsed;
}

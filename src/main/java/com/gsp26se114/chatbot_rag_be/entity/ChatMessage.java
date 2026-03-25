package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Một tin nhắn trong phiên hội thoại.
 * role = 'USER' → câu hỏi từ nhân viên
 * role = 'ASSISTANT' → câu trả lời của chatbot (có source chunks)
 */
@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_messages_session", columnList = "session_id"),
    @Index(name = "idx_chat_messages_tenant", columnList = "tenant_id"),
    @Index(name = "idx_chat_messages_role", columnList = "role")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "message_id")
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    /**
     * 'USER' hoặc 'ASSISTANT'
     */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Danh sách document chunks được dùng để trả lời (chỉ có với ASSISTANT).
     * Ví dụ: [{"chunk_id": "...", "document_name": "...", "similarity_score": 0.95}]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_chunks", columnDefinition = "jsonb")
    private List<Object> sourceChunks;

    @Column(name = "tokens_used")
    @Builder.Default
    private Integer tokensUsed = 0;

    /** Đánh giá câu trả lời ASSISTANT (1-5 sao) */
    @Column(name = "rating")
    private Short rating;

    @Column(name = "feedback_text", length = 1000)
    private String feedbackText;

    @Column(name = "rated_at")
    private LocalDateTime ratedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

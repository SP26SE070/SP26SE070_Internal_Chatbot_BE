package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phiên hội thoại giữa user và chatbot.
 * Mỗi session tập hợp nhiều ChatMessage liên tiếp.
 */
@Entity
@Table(name = "chat_sessions", indexes = {
    @Index(name = "idx_chat_sessions_tenant", columnList = "tenant_id"),
    @Index(name = "idx_chat_sessions_user", columnList = "user_id"),
    @Index(name = "idx_chat_sessions_status", columnList = "status")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Tiêu đề phiên (auto-generate từ câu hỏi đầu) */
    @Column(length = 500)
    private String title;

    /**
     * Trạng thái: ACTIVE, ENDED, ARCHIVED
     */
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "total_messages")
    private Integer totalMessages = 0;

    @Column(name = "total_tokens_used")
    private Integer totalTokensUsed = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

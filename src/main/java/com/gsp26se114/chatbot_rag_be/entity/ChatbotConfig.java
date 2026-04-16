package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cấu hình chatbot per-tenant (UI/UX).
 * 1-1 với tenants. TENANT_ADMIN có thể chỉnh sửa.
 */
@Entity
@Table(name = "chatbot_configs")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatbotConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "config_id")
    private UUID id;

    /** 1-1 với tenant */
    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    /** Tên hiển thị của chatbot */
    @Column(name = "bot_name", length = 100)
    private String botName = "AI Assistant";

    /** Tin nhắn chào khi mở chat */
    @Column(name = "welcome_message", columnDefinition = "TEXT")
    private String welcomeMessage = "Xin chào! Tôi có thể giúp gì cho bạn?";

    /** Trả lời khi không tìm thấy tài liệu liên quan */
    @Column(name = "fallback_message", columnDefinition = "TEXT")
    private String fallbackMessage = "Xin lỗi, tôi không tìm thấy thông tin phù hợp trong tài liệu nội bộ.";

    /** Ngôn ngữ mặc định: vi, en */
    @Column(length = 10)
    private String language = "vi";

    /** Giới hạn số tin nhắn tối đa mỗi user mỗi ngày */
    @Column(name = "max_messages_per_day")
    private Integer maxMessagesPerDay = 100;

    /** Giới hạn độ dài mỗi tin nhắn (ký tự) */
    @Column(name = "max_message_length")
    private Integer maxMessageLength = 500;

    /** Tự động kết thúc session sau N phút không hoạt động */
    @Column(name = "session_timeout_minutes")
    private Integer sessionTimeoutMinutes = 30;

    /** Number of chunks to retrieve from vector DB (default 3) */
    @Column(name = "top_k")
    private Integer topK = 3;

    /** Similarity threshold for RAG chunk retrieval (default 0.7) */
    @Column(name = "similarity_threshold")
    private Double similarityThreshold = 0.7;

    /** Chatbot response mode: BALANCED (default), STRICT, FLEXIBLE */
    @Column(name = "mode", length = 20)
    private String mode = "BALANCED";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

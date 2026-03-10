package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Thông báo in-app gửi tới user.
 * Ví dụ: duyệt tenant, subscription sắp hết hạn, xử lý tài liệu xong.
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_recipient", columnList = "recipient_user_id"),
    @Index(name = "idx_notifications_tenant", columnList = "tenant_id")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notification_id")
    private UUID id;

    /** NULL = broadcast tới toàn bộ tenant */
    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    /**
     * Loại thông báo:
     * TENANT_APPROVED, SUBSCRIPTION_EXPIRING, DOCUMENT_PROCESSED,
     * DOCUMENT_UPLOAD_FAILED, USER_CREATED, ROLE_GRANTED, ...
     */
    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    /** Link redirect khi user click */
    @Column(name = "action_url", length = 500)
    private String actionUrl;

    /** Extra data tuỳ theo type */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

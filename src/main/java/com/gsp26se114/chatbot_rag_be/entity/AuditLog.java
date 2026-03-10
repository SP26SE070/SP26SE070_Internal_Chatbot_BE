package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Log mọi hành động quan trọng trong hệ thống (security & compliance).
 * Không bao giờ xóa bảng này — đây là audit trail.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_logs_tenant", columnList = "tenant_id"),
    @Index(name = "idx_audit_logs_user", columnList = "user_id"),
    @Index(name = "idx_audit_logs_action", columnList = "action"),
    @Index(name = "idx_audit_logs_created", columnList = "created_at")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_log_id")
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    /** Denormalized để log vẫn giữ sau khi xóa user */
    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "user_role", length = 100)
    private String userRole;

    /**
     * Hành động: USER_LOGIN, USER_LOGOUT, DOCUMENT_UPLOAD, DOCUMENT_DELETE,
     * ROLE_GRANTED, ROLE_REVOKED, TENANT_APPROVED, SUBSCRIPTION_CREATED, ...
     */
    @Column(nullable = false, length = 100)
    private String action;

    /** Loại entity bị tác động: User, Document, Subscription, Role */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    /** UUID của entity bị tác động */
    @Column(name = "entity_id", length = 255)
    private String entityId;

    /** Giá trị trước khi thay đổi */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private Map<String, Object> oldValue;

    /** Giá trị sau khi thay đổi */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private Map<String, Object> newValue;

    @Column(length = 1000)
    private String description;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Kết quả: SUCCESS, FAILED
     */
    @Column(nullable = false, length = 20)
    private String status = "SUCCESS";

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

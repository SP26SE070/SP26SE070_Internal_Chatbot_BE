package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Normalize quyền bổ sung (permissions) của user thành bảng riêng.
 * Thay thế cho cột JSONB permissions trong bảng users.
 * TENANT_ADMIN cấp/thu hồi quyền tại đây.
 */
@Entity
@Table(name = "user_permission_grants",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "permission"}),
    indexes = {
        @Index(name = "idx_permission_grants_user", columnList = "user_id"),
        @Index(name = "idx_permission_grants_tenant", columnList = "tenant_id")
    }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPermissionGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "grant_id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Quyền được cấp: DOCUMENT_READ, DOCUMENT_WRITE,
     * DOCUMENT_DELETE, ANALYTICS_VIEW, DOCUMENT_DASHBOARD
     */
    @Column(nullable = false, length = 100)
    private String permission;

    /** TENANT_ADMIN đã cấp quyền này */
    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt = LocalDateTime.now();

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(length = 500)
    private String note;
}

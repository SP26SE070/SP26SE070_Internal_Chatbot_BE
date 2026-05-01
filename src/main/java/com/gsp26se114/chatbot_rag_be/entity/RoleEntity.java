package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Integer id;

    @Column(unique = true, nullable = false, length = 50)
    private String code; // SUPER_ADMIN, STAFF, TENANT_ADMIN, EMPLOYEE, CUSTOM_*

    @Column(nullable = false, length = 100)
    private String name; // Super Admin, Platform Staff, Tenant Admin, Employee

    @Column(name = "level", nullable = false)
    private Integer level = 4;

    @Column(length = 500)
    private String description;

    @Column(name = "tenant_id")
    private UUID tenantId; // NULL = system/fixed role, NOT NULL = custom role for specific tenant

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 20)
    private RoleType roleType = RoleType.FIXED; // SYSTEM, FIXED, CUSTOM

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", columnDefinition = "jsonb")
    private List<String> permissions = new ArrayList<>(); // ["USER_READ", "USER_WRITE", ...]

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // ========== HELPER METHODS ==========
    
    public boolean isSystemRole() {
        return roleType == RoleType.SYSTEM;
    }
    
    public boolean isTenantFixedRole() {
        return roleType == RoleType.FIXED;
    }
    
    public boolean isCustomRole() {
        return roleType == RoleType.CUSTOM;
    }
    
    public boolean belongsToTenant(UUID tenantId) {
        return this.tenantId != null && this.tenantId.equals(tenantId);
    }
    
    public boolean hasPermission(String permission) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        // Check for ALL permission (SUPER_ADMIN)
        if (permissions.contains("ALL")) {
            return true;
        }
        // Check direct permission
        if (permissions.contains(permission)) {
            return true;
        }
        // Check wildcard permission (e.g., USER_ALL includes USER_READ, USER_WRITE, USER_DELETE)
        if (permission.contains("_")) {
            String category = permission.split("_")[0];
            return permissions.contains(category + "_ALL");
        }
        return false;
    }
}

package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for Role entity
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleResponse {
    
    private Integer id;
    private String code;
    private String name;
    private Integer level;
    private String description;
    private UUID tenantId;
    private String tenantName; // For display purposes
    
    // Role Type
    private String roleType; // SYSTEM, FIXED, CUSTOM
    private Boolean isSystemRole; // roleType = SYSTEM
    private Boolean isFixed; // roleType = FIXED
    private Boolean isCustom; // roleType = CUSTOM
    
    // Permissions
    private List<String> permissions; // ["USER_READ", "USER_WRITE", ...]
    
    /**
     * Số user trong tenant có {@code user.role_id} = {@code id} của role này (fixed + custom).
     * Gồm ACTIVE và INACTIVE; không có soft-delete riêng ngoài {@code isActive}.
     */
    private Long usersCount;
    
    // Audit
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

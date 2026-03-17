package com.gsp26se114.chatbot_rag_be.constants;

import java.util.Set;

/**
 * Permission constants cho hệ thống
 * Mỗi permission định nghĩa quyền truy cập cụ thể
 */
public class PermissionConstants {
    
    // ========== USER MANAGEMENT ==========
    public static final String USER_READ = "USER_READ";
    public static final String USER_WRITE = "USER_WRITE";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String USER_ALL = "USER_ALL";
    
    // ========== DEPARTMENT MANAGEMENT ==========
    public static final String DEPT_READ = "DEPT_READ";
    public static final String DEPT_WRITE = "DEPT_WRITE";
    public static final String DEPT_DELETE = "DEPT_DELETE";
    public static final String DEPT_ALL = "DEPT_ALL";
    
    // ========== ROLE MANAGEMENT ==========
    public static final String ROLE_READ = "ROLE_READ";
    public static final String ROLE_WRITE = "ROLE_WRITE";
    public static final String ROLE_ALL = "ROLE_ALL";
    
    // ========== DOCUMENT MANAGEMENT ==========
    public static final String DOCUMENT_READ = "DOCUMENT_READ";
    public static final String DOCUMENT_WRITE = "DOCUMENT_WRITE";
    public static final String DOCUMENT_DELETE = "DOCUMENT_DELETE";
    public static final String DOCUMENT_ALL = "DOCUMENT_ALL";
    
    // ========== SUBSCRIPTION MANAGEMENT ==========
    public static final String SUBSCRIPTION_MANAGE = "SUBSCRIPTION_MANAGE";
    
    // ========== ANALYTICS ==========
    public static final String ANALYTICS_VIEW = "ANALYTICS_VIEW";
    
    // ========== PROFILE ==========
    public static final String PROFILE_MANAGE = "PROFILE_MANAGE";
    
    // ========== TENANT APPROVAL (STAFF) ==========
    public static final String TENANT_APPROVE = "TENANT_APPROVE";
    
    // ========== ALL PERMISSIONS (SUPER_ADMIN) ==========
    public static final String ALL = "ALL";
    
    // ========== VALID PERMISSIONS SET ==========
    private static final Set<String> VALID_PERMISSIONS = Set.of(
        USER_READ, USER_WRITE, USER_DELETE, USER_ALL,
        DEPT_READ, DEPT_WRITE, DEPT_DELETE, DEPT_ALL,
        ROLE_READ, ROLE_WRITE, ROLE_ALL,
        DOCUMENT_READ, DOCUMENT_WRITE, DOCUMENT_DELETE, DOCUMENT_ALL,
        SUBSCRIPTION_MANAGE, ANALYTICS_VIEW, PROFILE_MANAGE,
        TENANT_APPROVE, ALL
    );
    
    /**
     * Kiểm tra permission có hợp lệ không
     */
    public static boolean isValid(String permission) {
        return VALID_PERMISSIONS.contains(permission);
    }
    
    /**
     * Lấy tất cả permissions hợp lệ
     */
    public static Set<String> getAllPermissions() {
        return VALID_PERMISSIONS;
    }
    
    /**
     * Permissions chỉ SUPER_ADMIN có thể gán
     */
    public static Set<String> getSystemOnlyPermissions() {
        return Set.of(ALL, TENANT_APPROVE);
    }

    /**
     * Permissions forbidden for custom roles
     * Only TENANT_ADMIN can have these
     */
    public static Set<String> getCustomRoleForbiddenPermissions() {
        return Set.of(
            ALL,                  // Super admin only
            TENANT_APPROVE,       // Staff only
            USER_ALL,             // Admin only
            USER_WRITE,           // Admin only
            USER_DELETE,          // Admin only
            DEPT_ALL,             // Admin only
            DEPT_WRITE,           // Admin only
            DEPT_DELETE,          // Admin only
            ROLE_ALL,             // Admin only
            ROLE_WRITE,           // Admin only
            ROLE_READ,            // Admin only
            SUBSCRIPTION_MANAGE,  // Admin only
            ANALYTICS_VIEW        // Admin only
        );
    }
}

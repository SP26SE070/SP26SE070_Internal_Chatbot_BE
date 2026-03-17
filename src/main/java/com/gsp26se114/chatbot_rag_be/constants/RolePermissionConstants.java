package com.gsp26se114.chatbot_rag_be.constants;

import java.util.List;
import java.util.Map;

/**
 * Định nghĩa các quyền cơ bản cho mỗi role
 * Đây là quyền mặc định, không cần lưu vào DB
 * 
 * TENANT_ADMIN có thể cấp thêm quyền bổ sung cho user (lưu trong user_permissions table)
 */
public class RolePermissionConstants {
    
    /**
     * Quyền cơ bản của SUPER_ADMIN
     */
    public static final List<String> SUPER_ADMIN_PERMISSIONS = List.of(
        PermissionConstants.ALL
    );
    
    /**
     * Quyền cơ bản của STAFF (Platform Staff)
     */
    public static final List<String> STAFF_PERMISSIONS = List.of(
        PermissionConstants.TENANT_APPROVE,
        PermissionConstants.PROFILE_MANAGE
    );
    
    /**
     * Quyền cơ bản của TENANT_ADMIN
     */
    public static final List<String> TENANT_ADMIN_PERMISSIONS = List.of(
        PermissionConstants.USER_ALL,
        PermissionConstants.DEPT_ALL,
        PermissionConstants.ROLE_ALL,
        PermissionConstants.SUBSCRIPTION_MANAGE,
        PermissionConstants.ANALYTICS_VIEW,
        PermissionConstants.DOCUMENT_ALL,
        PermissionConstants.PROFILE_MANAGE
    );
    
    /**
     * Quyền cơ bản của EMPLOYEE (nhân viên thông thường)
     * Chỉ có quyền quản lý profile của mình
     */
    public static final List<String> EMPLOYEE_PERMISSIONS = List.of(
        PermissionConstants.PROFILE_MANAGE
    );
    
    /**
     * Quyền cơ bản của CONTENT_MANAGER
     * (Nếu có role này trong future)
     */
    public static final List<String> CONTENT_MANAGER_PERMISSIONS = List.of(
        PermissionConstants.DOCUMENT_ALL,
        PermissionConstants.ANALYTICS_VIEW,
        PermissionConstants.PROFILE_MANAGE
    );
    
    /**
     * Map từ role code sang basic permissions
     */
    public static final Map<String, List<String>> ROLE_BASIC_PERMISSIONS = Map.of(
        "SUPER_ADMIN", SUPER_ADMIN_PERMISSIONS,
        "STAFF", STAFF_PERMISSIONS,
        "TENANT_ADMIN", TENANT_ADMIN_PERMISSIONS,
        "EMPLOYEE", EMPLOYEE_PERMISSIONS,
        "CONTENT_MANAGER", CONTENT_MANAGER_PERMISSIONS
    );
    
    /**
     * Lấy basic permissions của một role
     */
    public static List<String> getBasicPermissions(String roleCode) {
        return ROLE_BASIC_PERMISSIONS.getOrDefault(roleCode, List.of());
    }
    
    /**
     * Các quyền có thể được TENANT_ADMIN cấp thêm cho user
     * (không bao gồm quyền hệ thống)
     */
    public static final List<String> GRANTABLE_PERMISSIONS = List.of(
        // Document permissions
        PermissionConstants.DOCUMENT_READ,
        PermissionConstants.DOCUMENT_WRITE,
        PermissionConstants.DOCUMENT_DELETE,
        PermissionConstants.DOCUMENT_ALL,

        // User management (chỉ read)
        PermissionConstants.USER_READ
    );
    
    /**
     * Kiểm tra permission có thể được cấp không
     */
    public static boolean isGrantable(String permission) {
        return GRANTABLE_PERMISSIONS.contains(permission);
    }
}

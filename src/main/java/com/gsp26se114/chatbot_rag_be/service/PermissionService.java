package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.constants.PermissionConstants;
import com.gsp26se114.chatbot_rag_be.constants.RolePermissionConstants;
import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.payload.response.PermissionCategoryResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.PermissionResponse;
import com.gsp26se114.chatbot_rag_be.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {
    
    private final RoleRepository roleRepository;
    
    /**
     * Kiểm tra user có permission cụ thể không
     * Kiểm tra theo thứ tự:
     * 1. Basic role permissions (từ RolePermissionConstants)
     * 2. User-specific permissions (trong user.permissions field)
     */
    public boolean hasPermission(User user, String requiredPermission) {
        if (user == null || user.getRoleId() == null) {
            return false;
        }
        
        // 1. Kiểm tra basic role permissions
        RoleEntity role = roleRepository.findById(user.getRoleId()).orElse(null);
        if (role == null) {
            return false;
        }
        
        List<String> basicPermissions = RolePermissionConstants.getBasicPermissions(role.getCode());
        
        // Check for ALL permission (SUPER_ADMIN)
        if (basicPermissions.contains(PermissionConstants.ALL)) {
            return true;
        }
        
        // Check direct permission
        if (basicPermissions.contains(requiredPermission)) {
            return true;
        }
        
        // Check wildcard permission (e.g., USER_ALL includes USER_READ, USER_WRITE, USER_DELETE)
        if (requiredPermission.contains("_")) {
            String category = requiredPermission.split("_")[0];
            if (basicPermissions.contains(category + "_ALL")) {
                return true;
            }
        }
        
        // 2. Kiểm tra user-specific permissions (trong user.permissions)
        if (user.getPermissions() != null && !user.getPermissions().isEmpty()) {
            // Check direct permission
            if (user.getPermissions().contains(requiredPermission)) {
                return true;
            }
            
            // Check wildcard in user permissions
            if (requiredPermission.contains("_")) {
                String category = requiredPermission.split("_")[0];
                if (user.getPermissions().contains(category + "_ALL")) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Yêu cầu user phải có permission (throw exception nếu không có)
     */
    public void requirePermission(User user, String permission) {
        if (!hasPermission(user, permission)) {
            throw new RuntimeException("Missing required permission: " + permission);
        }
    }
    
    /**
     * Kiểm tra user có bất kỳ permission nào trong list
     */
    public boolean hasAnyPermission(User user, String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(user, permission)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Kiểm tra user có tất cả permissions trong list
     */
    public boolean hasAllPermissions(User user, String... permissions) {
        for (String permission : permissions) {
            if (!hasPermission(user, permission)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Lấy danh sách permissions available cho TENANT_ADMIN tạo custom roles
     */
    public List<PermissionCategoryResponse> getAvailablePermissions() {
        List<PermissionCategoryResponse> categories = new ArrayList<>();

        // User Management — read only
        categories.add(PermissionCategoryResponse.builder()
                .category("User Management")
                .permissions(Arrays.asList(
                        new PermissionResponse(PermissionConstants.USER_READ,
                            "View users", "Xem danh sách người dùng")
                ))
                .build());

        // Department Management — read only
        categories.add(PermissionCategoryResponse.builder()
                .category("Department Management")
                .permissions(Arrays.asList(
                        new PermissionResponse(PermissionConstants.DEPT_READ,
                            "View departments", "Xem danh sách phòng ban")
                ))
                .build());

        // Document Management — full allowed
        categories.add(PermissionCategoryResponse.builder()
                .category("Document Management")
                .permissions(Arrays.asList(
                        new PermissionResponse(PermissionConstants.DOCUMENT_READ,
                            "View documents", "Xem tài liệu"),
                        new PermissionResponse(PermissionConstants.DOCUMENT_WRITE,
                            "Upload/Edit documents", "Upload và sửa tài liệu"),
                        new PermissionResponse(PermissionConstants.DOCUMENT_DELETE,
                            "Delete documents", "Xóa tài liệu"),
                        new PermissionResponse(PermissionConstants.DOCUMENT_ALL,
                            "All document permissions", "Tất cả quyền quản lý tài liệu")
                ))
                .build());

        // Profile
        categories.add(PermissionCategoryResponse.builder()
                .category("Profile")
                .permissions(Arrays.asList(
                        new PermissionResponse(PermissionConstants.PROFILE_MANAGE,
                            "Manage own profile", "Quản lý thông tin cá nhân")
                ))
                .build());

        return categories;
    }
    
    /**
     * Lấy tất cả permissions của user (basic + user-specific)
     */
    public List<String> getAllUserPermissions(User user) {
        List<String> allPermissions = new ArrayList<>();
        
        if (user == null || user.getRoleId() == null) {
            return allPermissions;
        }
        
        // 1. Add basic role permissions
        RoleEntity role = roleRepository.findById(user.getRoleId()).orElse(null);
        if (role != null) {
            allPermissions.addAll(RolePermissionConstants.getBasicPermissions(role.getCode()));
        }
        
        // 2. Add user-specific permissions
        if (user.getPermissions() != null) {
            allPermissions.addAll(user.getPermissions());
        }
        
        return allPermissions;
    }
    
    /**
     * Validate permissions list
     */
    public void validatePermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("Permissions không được để trống");
        }

        Set<String> forbidden = PermissionConstants.getCustomRoleForbiddenPermissions();

        for (String permission : permissions) {
            // Check permission exists in the system
            if (!PermissionConstants.isValid(permission)) {
                throw new IllegalArgumentException(
                    "Permission '" + permission + "' không tồn tại trong hệ thống."
                );
            }
            // Check permission not forbidden for custom roles
            if (forbidden.contains(permission)) {
                throw new IllegalArgumentException(
                    "Permission '" + permission + "' không thể gán cho custom role. " +
                    "Đây là quyền dành riêng cho TENANT_ADMIN."
                );
            }
        }
    }
}

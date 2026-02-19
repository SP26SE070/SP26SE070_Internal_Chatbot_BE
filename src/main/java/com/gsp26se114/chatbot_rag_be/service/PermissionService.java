package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.constants.PermissionConstants;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {
    
    private final RoleRepository roleRepository;
    
    /**
     * Kiểm tra user có permission cụ thể không
     */
    public boolean hasPermission(User user, String requiredPermission) {
        if (user == null || user.getRoleId() == null) {
            return false;
        }
        
        RoleEntity role = roleRepository.findById(user.getRoleId())
                .orElse(null);
        
        if (role == null || role.getPermissions() == null) {
            return false;
        }
        
        // Use RoleEntity's hasPermission method
        return role.hasPermission(requiredPermission);
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
        
        // User Management
        categories.add(PermissionCategoryResponse.builder()
                .category("User Management")
                .permissions(Arrays.asList(
                        new PermissionResponse(PermissionConstants.USER_READ, "View users", "Xem danh sách người dùng"),
                        new PermissionResponse(PermissionConstants.USER_WRITE, "Create/Edit users", "Tạo và sửa người dùng"),
                        new PermissionResponse(PermissionConstants.USER_DELETE, "Delete users", "Xóa người dùng"),
                        new PermissionResponse(PermissionConstants.USER_ALL, "All user permissions", "Tất cả quyền quản lý người dùng")
                ))
                .build());
        
        // Department Management
        categories.add(PermissionCategoryResponse.builder()
                .category("Department Management")
                .permissions(Arrays.asList(
                        new PermissionResponse(PermissionConstants.DEPT_READ, "View departments", "Xem danh sách phòng ban"),
                        new PermissionResponse(PermissionConstants.DEPT_WRITE, "Create/Edit departments", "Tạo và sửa phòng ban"),
                        new PermissionResponse(PermissionConstants.DEPT_DELETE, "Delete departments", "Xóa phòng ban"),
                        new PermissionResponse(PermissionConstants.DEPT_ALL, "All department permissions", "Tất cả quyền quản lý phòng ban")
                ))
                .build());
        
        // Role Management
        categories.add(PermissionCategoryResponse.builder()
                .category("Role Management")
                .permissions(Arrays.asList(
                        new PermissionResponse(PermissionConstants.ROLE_READ, "View roles", "Xem danh sách roles"),
                        new PermissionResponse(PermissionConstants.ROLE_WRITE, "Create/Edit custom roles", "Tạo và sửa custom roles"),
                        new PermissionResponse(PermissionConstants.ROLE_ALL, "All role permissions", "Tất cả quyền quản lý roles")
                ))
                .build());
        
        // Document Management
        categories.add(PermissionCategoryResponse.builder()
                .category("Document Management")
                .permissions(Arrays.asList(
                        new PermissionResponse(PermissionConstants.DOCUMENT_READ, "View documents", "Xem tài liệu"),
                        new PermissionResponse(PermissionConstants.DOCUMENT_WRITE, "Upload/Edit documents", "Upload và sửa tài liệu"),
                        new PermissionResponse(PermissionConstants.DOCUMENT_DELETE, "Delete documents", "Xóa tài liệu"),
                        new PermissionResponse(PermissionConstants.DOCUMENT_ALL, "All document permissions", "Tất cả quyền quản lý tài liệu")
                ))
                .build());
        
        // Subscription Management
        categories.add(PermissionCategoryResponse.builder()
                .category("Subscription Management")
                .permissions(Arrays.asList(
                        new PermissionResponse(PermissionConstants.SUBSCRIPTION_MANAGE, "Manage subscription", "Quản lý gói subscription")
                ))
                .build());
        
        // Analytics
        categories.add(PermissionCategoryResponse.builder()
                .category("Analytics")
                .permissions(Arrays.asList(
                        new PermissionResponse(PermissionConstants.ANALYTICS_VIEW, "View analytics", "Xem báo cáo và thống kê")
                ))
                .build());
        
        // Profile
        categories.add(PermissionCategoryResponse.builder()
                .category("Profile")
                .permissions(Arrays.asList(
                        new PermissionResponse(PermissionConstants.PROFILE_MANAGE, "Manage own profile", "Quản lý thông tin cá nhân")
                ))
                .build());
        
        return categories;
    }
    
    /**
     * Validate permissions list
     */
    public void validatePermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new RuntimeException("Permissions không được để trống");
        }
        
        for (String permission : permissions) {
            if (!PermissionConstants.isValid(permission)) {
                throw new RuntimeException("Invalid permission: " + permission);
            }
            
            // TENANT_ADMIN cannot assign system-only permissions
            if (PermissionConstants.getSystemOnlyPermissions().contains(permission)) {
                throw new RuntimeException("Cannot assign system-level permission: " + permission);
            }
        }
    }
}

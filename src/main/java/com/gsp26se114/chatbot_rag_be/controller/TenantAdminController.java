package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.payload.request.CreateUserRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateUserPermissionsRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateUserRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.TenantAnalyticsResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.UserResponse;
import com.gsp26se114.chatbot_rag_be.service.TenantAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant-admin")
@RequiredArgsConstructor
@Tag(name = "11. 👥 Tenant Admin - User Management", description = "Quản lý users trong tenant (TENANT_ADMIN)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TenantAdminController {
    
    private final TenantAdminService tenantAdminService;
    
    /**
     * Get tenant dashboard analytics
     */
    @GetMapping("/dashboard/analytics")
    @Operation(summary = "Lấy thống kê dashboard tenant", 
               description = "Thống kê tổng quan: users, departments, transfer requests")
    public ResponseEntity<TenantAnalyticsResponse> getTenantAnalytics(
            @AuthenticationPrincipal UserDetails userDetails) {
        TenantAnalyticsResponse analytics = tenantAdminService.getTenantAnalytics(userDetails.getUsername());
        return ResponseEntity.ok(analytics);
    }
    
    /**
     * Get all users in tenant
     */
    @GetMapping("/users")
    @Operation(summary = "Lấy danh sách users trong tenant", 
               description = "Tenant Admin xem tất cả users thuộc tenant của mình")
    public ResponseEntity<List<UserResponse>> getAllUsersInTenant(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<UserResponse> users = tenantAdminService.getAllUsersInTenant(userDetails.getUsername());
        return ResponseEntity.ok(users);
    }
    
    /**
     * Get user by ID (in same tenant only)
     */
    @GetMapping("/users/{userId}")
    @Operation(summary = "Xem chi tiết user", 
               description = "Xem thông tin chi tiết của user trong tenant")
    public ResponseEntity<UserResponse> getUserById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        UserResponse user = tenantAdminService.getUserById(userDetails.getUsername(), userId);
        return ResponseEntity.ok(user);
    }
    
    /**
     * Create new user (CONTENT_MANAGER or EMPLOYEE)
     */
    @PostMapping("/users")
    @Operation(summary = "Tạo user mới", 
               description = "Tạo CONTENT_MANAGER hoặc EMPLOYEE trong tenant")
    public ResponseEntity<UserResponse> createUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse user = tenantAdminService.createUser(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
    
    /**
     * Update user information
     */
    @PutMapping("/users/{userId}")
    @Operation(summary = "Cập nhật thông tin user", 
               description = "Cập nhật fullName, phoneNumber, departmentId, role")
    public ResponseEntity<UserResponse> updateUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse user = tenantAdminService.updateUser(userDetails.getUsername(), userId, request);
        return ResponseEntity.ok(user);
    }
    
    /**
     * Update user permissions (TENANT_ADMIN cấp quyền bổ sung cho user cụ thể)
     */
    @PutMapping("/users/{userId}/permissions")
    @Operation(summary = "Cập nhật quyền của user", 
               description = "TENANT_ADMIN cấp quyền bổ sung cho user (ví dụ: DOCUMENT_READ, DOCUMENT_WRITE, ANALYTICS_VIEW)")
    public ResponseEntity<UserResponse> updateUserPermissions(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserPermissionsRequest request) {
        UserResponse user = tenantAdminService.updateUserPermissions(
                userDetails.getUsername(), userId, request.getPermissions());
        return ResponseEntity.ok(user);
    }
    
    /**
     * Delete user (soft delete by removing from tenant)
     */
    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Xóa user", 
               description = "Xóa user khỏi tenant (không thể xóa chính mình)")
    public ResponseEntity<MessageResponse> deleteUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        tenantAdminService.deleteUser(userDetails.getUsername(), userId);
        return ResponseEntity.ok(new MessageResponse("User đã bị xóa khỏi tenant!"));
    }
    
    /**
     * Reset user password
     */
    @PostMapping("/users/{userId}/reset-password")
    @Operation(summary = "Reset mật khẩu user", 
               description = "Tạo mật khẩu mới và gửi email cho user")
    public ResponseEntity<MessageResponse> resetUserPassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        tenantAdminService.resetUserPassword(userDetails.getUsername(), userId);
        return ResponseEntity.ok(new MessageResponse("Mật khẩu mới đã được gửi đến email của user!"));
    }
}

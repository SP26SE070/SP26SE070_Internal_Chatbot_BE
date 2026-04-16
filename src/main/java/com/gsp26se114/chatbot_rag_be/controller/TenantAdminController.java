package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.AuditLog;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateUserRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateUserPermissionsRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateUserRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.PageResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.TenantAnalyticsResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.UserResponse;
import com.gsp26se114.chatbot_rag_be.service.TenantAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import java.time.LocalDateTime;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant-admin")
@RequiredArgsConstructor
@Tag(name = "12. 👥 Tenant Admin - User Management", description = "Quản lý users trong tenant (TENANT_ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;
    private final com.gsp26se114.chatbot_rag_be.repository.AuditLogRepository auditLogRepository;
    
    /**
     * Get tenant dashboard analytics
     */
    @GetMapping("/dashboard/analytics")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Lấy thống kê dashboard tenant",
               description = "Thống kê tổng quan: users, departments, transfer requests")
    public ResponseEntity<TenantAnalyticsResponse> getTenantAnalytics(
            @AuthenticationPrincipal UserDetails userDetails) {
        TenantAnalyticsResponse analytics = tenantAdminService.getTenantAnalytics(userDetails.getUsername());
        return ResponseEntity.ok(analytics);
    }

    /**
     * Get recent audit logs for tenant
     */
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get recent audit logs for tenant")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @AuthenticationPrincipal UserPrincipal userDetails,
            @RequestParam(defaultValue = "20") int limit) {

        List<AuditLog> logs = auditLogRepository
                .findRecentForDashboard(
                    LocalDateTime.now().plusMinutes(1),
                    org.springframework.data.domain.PageRequest.of(0, limit)
                )
                .stream()
                .filter(log -> userDetails.getTenantId().equals(log.getTenantId()))
                .toList();

        return ResponseEntity.ok(logs);
    }
    
    /**
     * Get all users in tenant
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('USER_WRITE')")
    @Operation(summary = "Lấy danh sách users trong tenant",
               description = """
                   **status:** `ACTIVE` (mặc định) | `INACTIVE` | `ALL` (ACTIVE + INACTIVE, không trùng lặp).
                   **roleId:** tuỳ chọn — lọc user có `roleId` khớp (cùng giá trị `id` trong GET /tenant-admin/roles).
                   Mỗi user có đúng một `roleId` (không multi-role).
                   """)
    public ResponseEntity<PageResponse<UserResponse>> getAllUsersInTenant(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "ACTIVE | INACTIVE | ALL")
            @RequestParam(value = "status", defaultValue = "ACTIVE") String status,
            @Parameter(description = "Lọc theo roles.role_id (optional)")
            @RequestParam(value = "roleId", required = false) Integer roleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<UserResponse> users = tenantAdminService.getAllUsersInTenant(
                userDetails.getUsername(), status, roleId);
        return ResponseEntity.ok(PageResponse.of(users, page, size));
    }
    
    /**
     * Get user by ID (in same tenant only)
     */
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('USER_WRITE')")
    @Operation(summary = "Xem chi tiết user", 
               description = "Xem thông tin chi tiết của user trong tenant")
    public ResponseEntity<UserResponse> getUserById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        UserResponse user = tenantAdminService.getUserById(userDetails.getUsername(), userId);
        return ResponseEntity.ok(user);
    }
    
    /**
     * Create new user (EMPLOYEE or custom role)
     */
    @PostMapping("/users")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Tạo user mới", 
               description = "Tạo EMPLOYEE hoặc custom role trong tenant")
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
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('USER_WRITE')")
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
    @PreAuthorize("hasRole('TENANT_ADMIN')")
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

    @PutMapping("/users/{userId}/activate")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('USER_WRITE')")
    @Operation(summary = "Kích hoạt user", 
               description = "Bật lại tài khoản user trong tenant (set isActive = true)")
    public ResponseEntity<UserResponse> activateUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        UserResponse user = tenantAdminService.activateUser(userDetails.getUsername(), userId);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/{userId}/deactivate")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('USER_WRITE')")
    @Operation(summary = "Vô hiệu hóa user", 
               description = "Tạm ngưng tài khoản user trong tenant (set isActive = false)")
    public ResponseEntity<UserResponse> deactivateUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        UserResponse user = tenantAdminService.deactivateUser(userDetails.getUsername(), userId);
        return ResponseEntity.ok(user);
    }
    
    /**
     * Delete user (remove from tenant)
     */
    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('USER_WRITE')")
    @Operation(summary = "Xóa user", 
               description = "Xóa user khỏi tenant (set isActive = false, tenantId = null). Khác với deactivate, hành động này không thể kích hoạt lại từ tenant hiện tại.")
    public ResponseEntity<MessageResponse> deleteUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        tenantAdminService.deleteUser(userDetails.getUsername(), userId);
        return ResponseEntity.ok(new MessageResponse("User đã được xóa khỏi tổ chức"));
    }
    
    /**
     * Reset user password
     */
    @PostMapping("/users/{userId}/reset-password")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Reset mật khẩu user", 
               description = "Tạo mật khẩu mới và gửi email cho user")
    public ResponseEntity<MessageResponse> resetUserPassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        tenantAdminService.resetUserPassword(userDetails.getUsername(), userId);
        return ResponseEntity.ok(new MessageResponse("Mật khẩu mới đã được gửi đến email của user!"));
    }
}

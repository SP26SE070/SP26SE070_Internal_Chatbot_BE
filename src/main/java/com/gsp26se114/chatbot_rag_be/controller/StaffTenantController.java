package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.entity.TenantStatus;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.EmailService;
import com.gsp26se114.chatbot_rag_be.service.StaffTenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/staff/tenants")
@RequiredArgsConstructor
@Tag(name = "08. 📋 Staff - Tenant Management", description = "Quản lý và phê duyệt tenants (STAFF)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('STAFF')")
public class StaffTenantController {

    private final TenantRepository tenantRepository;
    private final EmailService emailService;
    private final StaffTenantService staffTenantService;

    @GetMapping
    @Operation(summary = "Lấy tất cả tenants", description = "Xem danh sách tất cả tenants trong hệ thống")
    public ResponseEntity<List<Tenant>> getAllTenants() {
        List<Tenant> tenants = tenantRepository.findAll()
                .stream()
                .filter(tenant -> !Boolean.TRUE.equals(tenant.getMarkedForDeletion()))
                .toList();
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/pending")
    @Operation(summary = "Lấy danh sách tenants đang chờ duyệt", description = "Filter tenants theo trạng thái PENDING")
    public ResponseEntity<List<Tenant>> getPendingTenants() {
        List<Tenant> tenants = tenantRepository.findByStatus(TenantStatus.PENDING)
                .stream()
                .filter(tenant -> !Boolean.TRUE.equals(tenant.getMarkedForDeletion()))
                .toList();
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "Xem chi tiết tenant", description = "Lấy thông tin chi tiết của một tenant")
    public ResponseEntity<Tenant> getTenantById(@PathVariable UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .filter(tenant -> !Boolean.TRUE.equals(tenant.getMarkedForDeletion()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{tenantId}/approve")
    @Operation(summary = "Phê duyệt tenant", description = "Chuyển trạng thái tenant từ PENDING → ACTIVE")
    public ResponseEntity<MessageResponse> approveTenant(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID staffUserId = principal.getId();
        StaffTenantService.ApprovalResult result = staffTenantService.approveTenant(tenantId, staffUserId);

        try {
            emailService.sendTenantApprovalEmail(result.tenant(), result.loginEmail(), result.temporaryPassword());
        } catch (Exception e) {
            log.warn("Failed to send approval email for tenant {}: {}", tenantId, e.getMessage());
        }

        return ResponseEntity.ok(new MessageResponse("Tenant đã được phê duyệt"));
    }

    @PutMapping("/{tenantId}/resend-credentials")
    @Operation(
            summary = "Gửi lại thông tin đăng nhập tenant admin",
            description = "Tạo mật khẩu tạm mới cho tài khoản TENANT_ADMIN của tenant và gửi lại email thông tin đăng nhập."
    )
    public ResponseEntity<MessageResponse> resendTenantCredentials(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID staffUserId = principal.getId();
        StaffTenantService.ApprovalResult result =
                staffTenantService.resendTenantAdminCredentials(tenantId, staffUserId);

        try {
            emailService.sendTenantApprovalEmail(result.tenant(), result.loginEmail(), result.temporaryPassword());
        } catch (Exception e) {
            log.warn("Failed to resend credentials email for tenant {}: {}", tenantId, e.getMessage());
            throw new RuntimeException("Không thể gửi lại email thông tin đăng nhập. Vui lòng thử lại.");
        }

        return ResponseEntity.ok(new MessageResponse("Đã gửi lại thông tin đăng nhập cho tenant"));
    }

    @PutMapping("/{tenantId}/suspend")
    @Operation(summary = "Tạm ngưng tenant", description = "Chuyển trạng thái tenant sang SUSPENDED")
    public ResponseEntity<MessageResponse> suspendTenant(@PathVariable UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);

        return ResponseEntity.ok(new MessageResponse("Tenant đã bị tạm ngưng"));
    }

    @PutMapping("/{tenantId}/reject")
    @Operation(summary = "Từ chối tenant", description = "Chuyển trạng thái tenant sang REJECTED kèm lý do")
    public ResponseEntity<MessageResponse> rejectTenant(
            @PathVariable UUID tenantId,
            @RequestParam(name = "reason", required = false) String reason,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID staffUserId = principal.getId();
        Tenant tenant = staffTenantService.rejectTenant(tenantId, staffUserId, reason);

        try {
            emailService.sendTenantRejectedEmail(tenant);
        } catch (Exception e) {
            log.warn("Failed to send rejection email for tenant {}: {}", tenantId, e.getMessage());
        }

        return ResponseEntity.ok(new MessageResponse("Tenant đã bị từ chối"));
    }

    @PutMapping("/{tenantId}/activate")
    @Operation(summary = "Kích hoạt lại tenant", description = "Chuyển trạng thái tenant sang ACTIVE")
    public ResponseEntity<MessageResponse> activateTenant(@PathVariable UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);

        return ResponseEntity.ok(new MessageResponse("Tenant đã được kích hoạt"));
    }

    @DeleteMapping("/{tenantId}")
    @Operation(summary = "Xóa tenant", description = "Đánh dấu tenant để xóa theo chính sách lưu trữ dữ liệu")
    public ResponseEntity<MessageResponse> deleteTenant(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID staffUserId = principal.getId();
        staffTenantService.markTenantForDeletion(tenantId, staffUserId);
        return ResponseEntity.ok(new MessageResponse("Tenant đã được đánh dấu xóa"));
    }
}

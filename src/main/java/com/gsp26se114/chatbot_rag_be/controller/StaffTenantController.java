package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.entity.TenantStatus;
import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import com.gsp26se114.chatbot_rag_be.repository.RoleRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import com.gsp26se114.chatbot_rag_be.service.EmailService;
import com.gsp26se114.chatbot_rag_be.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    @Operation(summary = "Lấy tất cả tenants", description = "Xem danh sách tất cả tenants trong hệ thống")
    public ResponseEntity<List<Tenant>> getAllTenants() {
        List<Tenant> tenants = tenantRepository.findAll();
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/pending")
    @Operation(summary = "Lấy danh sách tenants đang chờ duyệt", description = "Filter tenants theo trạng thái PENDING")
    public ResponseEntity<List<Tenant>> getPendingTenants() {
        List<Tenant> tenants = tenantRepository.findByStatus(TenantStatus.PENDING);
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "Xem chi tiết tenant", description = "Lấy thông tin chi tiết của một tenant")
    public ResponseEntity<Tenant> getTenantById(@PathVariable UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{tenantId}/approve")
    @Operation(summary = "Phê duyệt tenant", description = "Chuyển trạng thái tenant từ PENDING → ACTIVE")
    public ResponseEntity<MessageResponse> approveTenant(@PathVariable UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        if (tenant.getStatus() != TenantStatus.PENDING) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Tenant không ở trạng thái PENDING"));
        }

        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setReviewedAt(LocalDateTime.now());
        tenantRepository.save(tenant);

        // Create TENANT_ADMIN user for this tenant
        String representativeName = tenant.getRepresentativeName() != null
                ? tenant.getRepresentativeName()
                : tenant.getContactEmail();

        RoleEntity tenantAdminRole = roleRepository.findByCode("TENANT_ADMIN")
                .orElseThrow(() -> new RuntimeException("Role TENANT_ADMIN không tồn tại"));

        String loginEmail = generateTenantAdminLoginEmail(tenant.getName());
        String temporaryPassword = com.gsp26se114.chatbot_rag_be.util.UserUtil.generateRandomPassword();

        User tenantAdminUser = new User();
        tenantAdminUser.setEmail(loginEmail);
        tenantAdminUser.setContactEmail(tenant.getContactEmail());
        tenantAdminUser.setPassword(passwordEncoder.encode(temporaryPassword));
        tenantAdminUser.setFullName(representativeName);
        tenantAdminUser.setPhoneNumber(tenant.getRepresentativePhone());
        tenantAdminUser.setRoleId(tenantAdminRole.getId());
        tenantAdminUser.setTenantId(tenant.getId());
        tenantAdminUser.setMustChangePassword(true);
        tenantAdminUser.setIsActive(true);
        tenantAdminUser.setCreatedAt(LocalDateTime.now());

        userRepository.save(tenantAdminUser);

        // Send approval email with credentials
        try {
            emailService.sendTenantApprovalEmail(tenant, loginEmail, temporaryPassword);
        } catch (Exception e) {
            log.error("Failed to send approval email", e);
        }

        return ResponseEntity.ok(new MessageResponse("Tenant đã được phê duyệt"));
    }

    private String generateTenantAdminLoginEmail(String tenantName) {
        String username = "admin";
        String tenantDomain = UserUtil.removeAccent(tenantName)
                .toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("[^a-z0-9]", "");

        String baseEmail = username + "@" + tenantDomain + ".com";
        String loginEmail = baseEmail;
        int suffix = 1;

        while (userRepository.existsByEmail(loginEmail)) {
            loginEmail = username + suffix + "@" + tenantDomain + ".com";
            suffix++;
        }

        return loginEmail;
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
            @RequestParam(name = "reason", required = false) String reason) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        if (tenant.getStatus() != TenantStatus.PENDING) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Chỉ có thể từ chối tenant đang ở trạng thái PENDING"));
        }

        tenant.setStatus(TenantStatus.REJECTED);
        tenant.setReviewedAt(LocalDateTime.now());
        tenant.setRejectionReason(reason);
        tenantRepository.save(tenant);

        try {
            emailService.sendTenantRejectedEmail(tenant);
        } catch (Exception e) {
            log.error("Failed to send tenant rejected email", e);
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
    @Operation(summary = "Xóa tenant", description = "Xóa tenant khỏi hệ thống (cẩn thận!)")
    public ResponseEntity<MessageResponse> deleteTenant(@PathVariable UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            return ResponseEntity.notFound().build();
        }

        tenantRepository.deleteById(tenantId);
        return ResponseEntity.ok(new MessageResponse("Tenant đã được xóa"));
    }
}

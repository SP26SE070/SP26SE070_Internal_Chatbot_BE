package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.entity.TenantStatus;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import com.gsp26se114.chatbot_rag_be.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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

    @GetMapping
    @Operation(summary = "Lấy tất cả tenants", description = "Xem danh sách tất cả tenants trong hệ thống")
    public ResponseEntity<List<Tenant>> getAllTenants() {
        List<Tenant> tenants = tenantRepository.findAll();
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

        // Send approval email
        try {
            emailService.sendTenantApprovalEmail(tenant);
        } catch (Exception e) {
            log.error("Failed to send approval email", e);
        }

        return ResponseEntity.ok(new MessageResponse("Tenant đã được phê duyệt"));
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

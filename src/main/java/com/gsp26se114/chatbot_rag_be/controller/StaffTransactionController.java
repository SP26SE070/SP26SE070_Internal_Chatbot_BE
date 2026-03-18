package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.PaymentStatus;
import com.gsp26se114.chatbot_rag_be.entity.PaymentTransaction;
import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.payload.response.PaymentTransactionResponse;
import com.gsp26se114.chatbot_rag_be.repository.PaymentTransactionRepository;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * STAFF: Quản lý giao dịch thanh toán của các tenant.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/staff/transactions")
@RequiredArgsConstructor
@Tag(name = "10. 💳 Staff - Transaction Management", description = "Quản lý giao dịch thanh toán của tenants (STAFF)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('STAFF')")
public class StaffTransactionController {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final TenantRepository tenantRepository;

    @GetMapping
    @Operation(summary = "Danh sách giao dịch", description = "Lấy tất cả giao dịch. Có thể lọc theo tenantId và status.")
    public ResponseEntity<List<PaymentTransactionResponse>> listTransactions(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String status) {

        List<PaymentTransaction> list;
        PaymentStatus statusEnum = parseStatus(status);

        if (tenantId != null && statusEnum != null) {
            list = paymentTransactionRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, statusEnum);
        } else if (tenantId != null) {
            list = paymentTransactionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        } else if (statusEnum != null) {
            list = paymentTransactionRepository.findByStatusOrderByCreatedAtDesc(statusEnum);
        } else {
            list = paymentTransactionRepository.findAllForStaffOrderByCreatedAtDesc();
        }

        Map<UUID, String> tenantNames = buildTenantNamesMap(list);
        List<PaymentTransactionResponse> response = list.stream()
                .map(pt -> toResponse(pt, tenantNames.get(pt.getTenantId())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/tenants/{tenantId}")
    @Operation(summary = "Giao dịch theo tenant", description = "Lấy danh sách giao dịch của một tenant. Có thể lọc theo status.")
    public ResponseEntity<List<PaymentTransactionResponse>> listByTenant(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String status) {

        List<PaymentTransaction> list;
        PaymentStatus statusEnum = parseStatus(status);

        if (statusEnum != null) {
            list = paymentTransactionRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, statusEnum);
        } else {
            list = paymentTransactionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }

        String tenantName = tenantRepository.findById(tenantId).map(Tenant::getName).orElse(null);
        Map<UUID, String> tenantNames = Map.of(tenantId, tenantName != null ? tenantName : "");
        List<PaymentTransactionResponse> response = list.stream()
                .map(pt -> toResponse(pt, tenantNames.get(pt.getTenantId())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết giao dịch", description = "Lấy thông tin một giao dịch theo ID.")
    public ResponseEntity<PaymentTransactionResponse> getById(@PathVariable UUID id) {
        return paymentTransactionRepository.findById(id)
                .map(pt -> {
                    String tenantName = tenantRepository.findById(pt.getTenantId())
                            .map(Tenant::getName).orElse(null);
                    return ResponseEntity.ok(toResponse(pt, tenantName));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private static PaymentStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return PaymentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Map<UUID, String> buildTenantNamesMap(List<PaymentTransaction> list) {
        List<UUID> tenantIds = list.stream()
                .map(PaymentTransaction::getTenantId)
                .distinct()
                .collect(Collectors.toList());
        if (tenantIds.isEmpty()) return Map.of();
        return tenantRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getId, Tenant::getName, (a, b) -> a));
    }

    private static PaymentTransactionResponse toResponse(PaymentTransaction pt, String tenantName) {
        return PaymentTransactionResponse.builder()
                .id(pt.getId())
                .tenantId(pt.getTenantId())
                .tenantName(tenantName)
                .subscriptionId(pt.getSubscription() != null ? pt.getSubscription().getId() : null)
                .amount(pt.getAmount())
                .currency(pt.getCurrency())
                .tier(pt.getTier())
                .status(pt.getStatus())
                .transactionCode(pt.getTransactionCode())
                .gateway(pt.getGateway())
                .createdAt(pt.getCreatedAt())
                .paidAt(pt.getPaidAt())
                .expiresAt(pt.getExpiresAt())
                .isAutoRenewal(pt.getIsAutoRenewal())
                .isExpired(pt.isExpired())
                .build();
    }
}

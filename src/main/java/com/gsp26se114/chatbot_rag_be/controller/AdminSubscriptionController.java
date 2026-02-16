package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.payload.response.SubscriptionResponse;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/subscriptions")
@RequiredArgsConstructor
@Tag(name = "05. 🔐 Super Admin - Subscription Management", description = "Quản lý subscriptions của tenants (SUPER_ADMIN)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminSubscriptionController {
    
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    
    /**
     * Get all subscriptions (all tenants)
     */
    @GetMapping
    @Operation(summary = "Lấy tất cả subscriptions", 
               description = "Lấy danh sách tất cả subscriptions của tất cả tenants")
    public ResponseEntity<List<SubscriptionResponse>> getAllSubscriptions() {
        log.info("Admin getting all subscriptions");
        List<Subscription> subscriptions = subscriptionRepository.findAll();
        List<SubscriptionResponse> responses = subscriptions.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
    
    /**
     * Get active subscriptions only
     */
    @GetMapping("/active")
    @Operation(summary = "Lấy các subscription đang active", 
               description = "Lấy danh sách subscriptions có status = ACTIVE")
    public ResponseEntity<List<SubscriptionResponse>> getActiveSubscriptions() {
        log.info("Admin getting active subscriptions");
        List<Subscription> subscriptions = subscriptionRepository.findAll().stream()
                .filter(s -> s.getStatus() == com.gsp26se114.chatbot_rag_be.entity.SubscriptionStatus.ACTIVE)
                .collect(Collectors.toList());
        List<SubscriptionResponse> responses = subscriptions.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
    
    /**
     * Get subscriptions by tenant
     */
    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "Lấy subscriptions của tenant", 
               description = "Lấy tất cả subscriptions của một tenant cụ thể")
    public ResponseEntity<List<SubscriptionResponse>> getSubscriptionsByTenant(
            @PathVariable UUID tenantId) {
        log.info("Admin getting subscriptions for tenant: {}", tenantId);
        List<Subscription> subscriptions = subscriptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<SubscriptionResponse> responses = subscriptions.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
    
    /**
     * Get subscription by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết subscription", 
               description = "Lấy thông tin chi tiết của subscription theo ID")
    public ResponseEntity<SubscriptionResponse> getSubscriptionById(@PathVariable UUID id) {
        log.info("Admin getting subscription: {}", id);
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + id));
        return ResponseEntity.ok(toResponse(subscription));
    }
    
    /**
     * Convert Subscription entity to response
     */
    private SubscriptionResponse toResponse(Subscription sub) {
        // Get tenant name
        String tenantName = tenantRepository.findById(sub.getTenantId())
                .map(Tenant::getName)
                .orElse("Unknown");
        
        return SubscriptionResponse.builder()
                .id(sub.getId())
                .tenantId(sub.getTenantId())
                .tenantName(tenantName)
                .tier(sub.getTier())
                .status(sub.getStatus())
                .price(sub.getPrice())
                .currency(sub.getCurrency())
                .billingCycle(sub.getBillingCycle())
                .startDate(sub.getStartDate())
                .endDate(sub.getEndDate())
                .nextBillingDate(sub.getNextBillingDate())
                .isTrial(sub.getIsTrial())
                .trialEndDate(sub.getTrialEndDate())
                .autoRenew(sub.getAutoRenew())
                .maxUsers(sub.getMaxUsers())
                .maxDocuments(sub.getMaxDocuments())
                .maxStorageGb(sub.getMaxStorageGb())
                .maxApiCalls(sub.getMaxApiCalls())
                .maxChatbotRequests(sub.getMaxChatbotRequests())
                .maxRagDocuments(sub.getMaxRagDocuments())
                .maxAiTokens(sub.getMaxAiTokens())
                .paymentMethod(sub.getPaymentMethod())
                .paymentGateway(sub.getPaymentGateway())
                .transactionCode(sub.getTransactionCode())
                .lastPaymentId(sub.getLastPaymentId())
                .lastPaymentDate(sub.getLastPaymentDate())
                .cancelledAt(sub.getCancelledAt())
                .cancellationReason(sub.getCancellationReason())
                .notes(sub.getNotes())
                .createdAt(sub.getCreatedAt())
                .updatedAt(sub.getUpdatedAt())
                .build();
    }
}

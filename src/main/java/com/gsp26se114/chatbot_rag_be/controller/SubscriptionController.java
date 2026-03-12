package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.PaymentTransaction;
import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.payload.request.CancelSubscriptionRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.SelectPlanRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.SubscriptionResponse;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {
    
    private final SubscriptionService subscriptionService;
    
    // ==================== SUPER ADMIN APIs MOVED TO AdminSubscriptionController ====================
    // ✅ All /api/v1/admin/subscriptions/* endpoints moved to AdminSubscriptionController (Tag 05)
    
    // ==================== TENANT_ADMIN APIs ====================
    
    /**
     * Select a paid subscription plan and initiate payment (TENANT_ADMIN)
     */
    @PostMapping("/api/v1/subscriptions/select-plan")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Tag(name = "15. 💳 Tenant Admin - Subscription Plans", description = "Quản lý gói subscription (TENANT_ADMIN)")
    @Operation(
        summary = "💳 Select Subscription Plan",
        description = """
            Tenant Admin chọn gói subscription và tạo payment.
            
            **Tier:**
            - STARTER: 500K VND/month
            - STANDARD: 2M VND/month
            - ENTERPRISE: 5M VND/month
            
            **Response:** QR code để chuyển tiền qua SePay
            """
    )
    public ResponseEntity<Map<String, Object>> selectPlan(
        @Valid @RequestBody SelectPlanRequest request,
        @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        log.info("Tenant {} selecting plan: {} - {}", userPrincipal.getTenantId(), request.getTier(), request.getCycle());

        try {
            PaymentTransaction payment = subscriptionService.selectPaidPlan(
                userPrincipal.getTenantId(),
                request.getTier(),
                request.getCycle(),
                userPrincipal.getId()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("payment_id", payment.getId());
            response.put("subscription_id", payment.getSubscription().getId());
            response.put("transaction_code", payment.getTransactionCode());
            response.put("amount", payment.getAmount());
            response.put("currency", payment.getCurrency());
            response.put("qr_image_url", payment.getQrImageUrl());
            response.put("qr_content", payment.getQrContent());
            response.put("expires_at", payment.getExpiresAt());
            response.put("tier", payment.getTier().name());
            response.put("billing_cycle", payment.getSubscription().getBillingCycle().name());
            response.put("bank_account", "02317500402");
            response.put("bank_name", "TPBANK");
            response.put("account_name", "PHAM HONG QUAN");
            response.put("polling_url", "/api/v1/payment/status/" + payment.getId());
            response.put("polling_interval_seconds", 5);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get current active subscription (All tenant users)
     */
    @GetMapping("/api/v1/subscriptions/current")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Tag(name = "15. 💳 Tenant Admin - Subscription Plans", description = "Quản lý gói subscription (TENANT_ADMIN)")
    @Operation(summary = "📋 Get Current Subscription")
    public ResponseEntity<Map<String, Object>> getCurrentSubscription(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            Subscription sub = subscriptionService.getActiveSubscription(userPrincipal.getTenantId());

            Map<String, Object> response = new HashMap<>();
            response.put("subscription_id", sub.getId());
            response.put("tenant_id", sub.getTenantId());
            response.put("tier", sub.getTier().name());
            response.put("status", sub.getStatus().name());
            response.put("is_trial", sub.getIsTrial());
            response.put("start_date", sub.getStartDate());
            response.put("end_date", sub.getEndDate());
            response.put("price", sub.getPrice());
            response.put("currency", sub.getCurrency());
            response.put("billing_cycle", sub.getBillingCycle().name());
            response.put("auto_renew", sub.getAutoRenew());
            response.put("ai_model", sub.getAiModel());
            response.put("max_users", sub.getMaxUsers());
            response.put("max_documents", sub.getMaxDocuments());
            response.put("max_api_calls", sub.getMaxApiCalls());

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", "No active subscription found"));
        }
    }

    /**
     * Cancel subscription (TENANT_ADMIN)
     */
    @PutMapping("/api/v1/subscriptions/cancel")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Tag(name = "15. 💳 Tenant Admin - Subscription Plans", description = "Quản lý gói subscription (TENANT_ADMIN)")
    @Operation(summary = "❌ Cancel Subscription")
    public ResponseEntity<Map<String, Object>> cancelSubscription(
        @Valid @RequestBody CancelSubscriptionRequest request,
        @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        try {
            subscriptionService.cancelSubscription(userPrincipal.getTenantId(), userPrincipal.getId(), request.getReason());
            Subscription sub = subscriptionService.getActiveSubscription(userPrincipal.getTenantId());

            return ResponseEntity.ok(Map.of(
                "message", "Subscription cancelled successfully",
                "subscription_id", sub.getId(),
                "cancelled_at", sub.getCancelledAt(),
                "remains_active_until", sub.getEndDate()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Toggle auto-renewal (TENANT_ADMIN)
     */
    @PutMapping("/api/v1/subscriptions/auto-renew")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Tag(name = "15. 💳 Tenant Admin - Subscription Plans", description = "Quản lý gói subscription (TENANT_ADMIN)")
    @Operation(summary = "🔄 Toggle Auto-Renewal")
    public ResponseEntity<Map<String, Object>> toggleAutoRenew(
        @RequestParam boolean autoRenew,
        @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        try {
            subscriptionService.toggleAutoRenew(userPrincipal.getTenantId(), autoRenew, userPrincipal.getId());
            Subscription sub = subscriptionService.getActiveSubscription(userPrincipal.getTenantId());

            return ResponseEntity.ok(Map.of(
                "message", autoRenew ? "Auto-renewal enabled" : "Auto-renewal disabled",
                "subscription_id", sub.getId(),
                "auto_renew", sub.getAutoRenew()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Helper: Map entity to response
     */
    @SuppressWarnings("unused")
    private SubscriptionResponse mapToResponse(Subscription s) {
        return SubscriptionResponse.builder()
                .id(s.getId())
                .tenantId(s.getTenantId())
                .tier(s.getTier())
                .status(s.getStatus())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .price(s.getPrice())
                .currency(s.getCurrency())
                .billingCycle(s.getBillingCycle())
                .isTrial(s.getIsTrial())
                .trialEndDate(s.getTrialEndDate())
                .maxUsers(s.getMaxUsers())
                .maxDocuments(s.getMaxDocuments())
                .maxStorageGb(s.getMaxStorageGb())
                .maxApiCalls(s.getMaxApiCalls())
                .autoRenew(s.getAutoRenew())
                .nextBillingDate(s.getNextBillingDate())
                .cancelledAt(s.getCancelledAt())
                .cancelledBy(s.getCancelledBy())
                .cancellationReason(s.getCancellationReason())
                .paymentMethod(s.getPaymentMethod())
                .lastPaymentId(s.getLastPaymentId())
                .lastPaymentDate(s.getLastPaymentDate())
                .createdAt(s.getCreatedAt())
                .createdBy(s.getCreatedBy())
                .updatedAt(s.getUpdatedAt())
                .updatedBy(s.getUpdatedBy())
                .notes(s.getNotes())
                .build();
    }
}

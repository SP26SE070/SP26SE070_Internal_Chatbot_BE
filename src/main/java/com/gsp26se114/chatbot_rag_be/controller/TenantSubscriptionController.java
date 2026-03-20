package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.payload.response.SubscriptionResponse;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenant-subscription")
@RequiredArgsConstructor
@Tag(name = "11. 💳 Tenant Admin - Subscription", description = "Xem và quản lý subscription (TENANT_ADMIN)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TenantSubscriptionController {
    
    private final SubscriptionService subscriptionService;
    
    /**
     * Lấy subscription hiện tại của tenant mình
     */
    @GetMapping("/my-subscription")
    @Operation(summary = "Xem subscription của tenant", 
               description = "TENANT_ADMIN xem thông tin subscription hiện tại của tenant mình")
    public ResponseEntity<SubscriptionResponse> getMySubscription(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        Subscription subscription = subscriptionService.getActiveSubscription(userPrincipal.getTenantId());
        return ResponseEntity.ok(mapToResponse(subscription));
    }
    
    /**
     * Helper: Map entity to response
     */
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
                .aiModel(s.getAiModel())
                .build();
    }
}

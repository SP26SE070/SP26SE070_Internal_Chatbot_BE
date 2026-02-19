package com.gsp26se114.chatbot_rag_be.payload.response;

import com.gsp26se114.chatbot_rag_be.entity.BillingCycle;
import com.gsp26se114.chatbot_rag_be.entity.SubscriptionStatus;
import com.gsp26se114.chatbot_rag_be.entity.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {
    private UUID id;
    private UUID tenantId;
    private String tenantName; // For admin view
    
    // Subscription Details
    private SubscriptionTier tier;
    private SubscriptionStatus status;
    
    // Billing Info
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BigDecimal price;
    private String currency;
    private BillingCycle billingCycle;
    
    // Trial Info
    private Boolean isTrial;
    private LocalDateTime trialEndDate;
    
    // Usage Limits
    private Integer maxUsers;
    private Integer maxDocuments;
    private Integer maxStorageGb;
    private Integer maxApiCalls;
    private Integer maxChatbotRequests;
    private Integer maxRagDocuments;
    private Long maxAiTokens;
    
    // Auto Renewal
    private Boolean autoRenew;
    private LocalDateTime nextBillingDate;
    
    // Cancellation
    private LocalDateTime cancelledAt;
    private UUID cancelledBy;
    private String cancellationReason;
    
    // Payment
    private String paymentMethod;
    private String paymentGateway;
    private String transactionCode;
    private String lastPaymentId;
    private LocalDateTime lastPaymentDate;
    
    // Audit
    private LocalDateTime createdAt;
    private UUID createdBy;
    private LocalDateTime updatedAt;
    private UUID updatedBy;
    
    private String notes;
}

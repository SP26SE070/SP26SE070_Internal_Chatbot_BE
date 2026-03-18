package com.gsp26se114.chatbot_rag_be.payload.response;

import com.gsp26se114.chatbot_rag_be.entity.PaymentStatus;
import com.gsp26se114.chatbot_rag_be.entity.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for payment transaction (Staff / Admin view).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionResponse {

    private UUID id;
    private UUID tenantId;
    private String tenantName;
    private UUID subscriptionId;
    private BigDecimal amount;
    private String currency;
    private SubscriptionTier tier;
    private PaymentStatus status;
    private String transactionCode;
    private String gateway;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime expiresAt;
    private Boolean isAutoRenewal;
    private Boolean isExpired;
}

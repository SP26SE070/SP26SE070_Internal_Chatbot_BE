package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing payment transactions for subscriptions.
 * Tracks payment history, status, and gateway responses.
 *
 * @author GSP26SE114
 * @version 1.0
 */
@Entity
@Table(name = "payment_transactions", indexes = {
    @Index(name = "idx_transaction_code", columnList = "transaction_code"),
    @Index(name = "idx_subscription_id", columnList = "subscription_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ========== RELATIONSHIP ==========
    
    /**
     * Reference to the subscription being paid for
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    /**
     * Tenant ID for quick filtering (denormalized for performance)
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // ========== PAYMENT DETAILS ==========

    /**
     * Amount to be paid (in VND)
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * Currency code (VND, USD)
     */
    @Column(nullable = false, length = 10)
    private String currency = "VND";

    /**
     * Unique transaction code sent to SePay
     * Format: THANHTOAN {TIER} {yyyyMMdd} SUB-{UUID}
     * Example: THANHTOAN STANDARD 20260129 SUB-abc123
     */
    @Column(name = "transaction_code", nullable = false, unique = true, length = 200)
    private String transactionCode;

    /**
     * Subscription tier being purchased
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SubscriptionTier tier;

    // ========== PAYMENT GATEWAY ==========

    /**
     * Payment gateway used (SEPAY, VNPAY, etc.)
     */
    @Column(nullable = false, length = 50)
    private String gateway = "SEPAY";

    /**
     * Transaction ID from payment gateway (SePay's transaction ID)
     */
    @Column(name = "gateway_transaction_id", length = 200)
    private String gatewayTransactionId;

    /**
     * Full response from payment gateway (stored as JSON)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gateway_response", columnDefinition = "jsonb")
    private Map<String, Object> gatewayResponse;

    // ========== STATUS TRACKING ==========

    /**
     * Payment status: PENDING, SUCCESS, FAILED, EXPIRED, CANCELLED
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status = PaymentStatus.PENDING;

    /**
     * Error message if payment failed
     */
    @Column(length = 1000)
    private String errorMessage;

    // ========== QR CODE INFORMATION ==========

    /**
     * QR code content for bank transfer
     */
    @Column(length = 500)
    private String qrContent;

    /**
     * QR code image URL (if generated)
     */
    @Column(length = 500)
    private String qrImageUrl;

    /**
     * Payment expiry time (typically 30 minutes from creation)
     */
    private LocalDateTime expiresAt;

    // ========== TIMESTAMPS ==========

    /**
     * When the payment was initiated
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * When the payment was completed/verified
     */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /**
     * Last update timestamp
     */
    private LocalDateTime updatedAt;

    // ========== AUDIT FIELDS ==========

    /**
     * User who initiated the payment
     */
    @Column(name = "created_by")
    private UUID createdBy;

    /**
     * Notes or additional information
     */
    @Column(length = 1000)
    private String notes;

    /**
     * Whether this is an auto-renewal payment
     */
    @Column(nullable = false)
    private Boolean isAutoRenewal = false;

    /**
     * Number of webhook retry attempts
     */
    @Column(nullable = false)
    private Integer webhookRetryCount = 0;

    /**
     * Last webhook received timestamp
     */
    private LocalDateTime lastWebhookAt;

    // ========== HELPER METHODS ==========

    /**
     * Check if payment is still pending and not expired
     */
    public boolean isActive() {
        return status == PaymentStatus.PENDING 
            && expiresAt != null 
            && LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * Check if payment has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Mark payment as successful
     */
    public void markAsPaid(String gatewayTxnId, Map<String, Object> response) {
        this.status = PaymentStatus.SUCCESS;
        this.gatewayTransactionId = gatewayTxnId;
        this.gatewayResponse = response;
        this.paidAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark payment as failed
     */
    public void markAsFailed(String error) {
        this.status = PaymentStatus.FAILED;
        this.errorMessage = error;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark payment as expired
     */
    public void markAsExpired() {
        if (this.status == PaymentStatus.PENDING) {
            this.status = PaymentStatus.EXPIRED;
            this.updatedAt = LocalDateTime.now();
        }
    }
}

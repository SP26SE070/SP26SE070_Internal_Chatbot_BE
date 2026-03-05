package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "subscription_id")
    private UUID id;
    
    // ========== RELATIONSHIP ==========
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId; // FK to Tenant

    @Column(name = "plan_id")
    private UUID planId; // FK to SubscriptionPlan (snapshot reference; nullable if plan is deleted)
    
    // ========== SUBSCRIPTION DETAILS ==========
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionTier tier; // TRIAL, STARTER, STANDARD, ENTERPRISE
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status; // ACTIVE, EXPIRED, CANCELLED, SUSPENDED
    
    // ========== BILLING INFORMATION ==========
    @Column(nullable = false)
    private LocalDateTime startDate;
    
    @Column(nullable = false)
    private LocalDateTime endDate;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal price; // Giá gói đăng ký (VND hoặc USD)
    
    @Column(length = 10)
    private String currency; // VND, USD
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BillingCycle billingCycle; // MONTHLY, QUARTERLY, YEARLY
    
    // ========== TRIAL INFORMATION ==========
    @Column(nullable = false)
    private Boolean isTrial = false; // Có phải gói trial không
    
    private LocalDateTime trialEndDate; // Ngày kết thúc trial
    
    // ========== USAGE LIMITS (dựa theo tier) ==========
    private Integer maxUsers; // Số user tối đa
    private Integer maxDocuments; // Số documents tối đa
    private Integer maxStorageGb; // Storage tối đa (GB)
    private Integer maxApiCalls; // API calls/month
    
    // ========== AI MODEL CONFIGURATION (Gemini) ==========
    @Column(length = 100)
    private String aiModel; // gemini-1.5-flash (TRIAL/STARTER), gemini-1.5-pro (STANDARD/ENTERPRISE)
    
    private Integer maxChatbotRequests; // Max chatbot requests per month
    
    private Long maxAiTokens; // Max AI tokens per month (for usage tracking)
    
    @Column(length = 50)
    private String contextWindowTokens; // Context window: 32k, 128k, 1M, 2M
    
    // ========== RAG CONFIGURATION ==========
    private Boolean enableRag = true; // Enable Retrieval-Augmented Generation
    
    private Integer maxRagDocuments; // Max documents for RAG context
    
    @Column(length = 50)
    private String embeddingModel; // text-embedding-004 (Google)
    
    private Integer ragChunkSize; // Chunk size for document splitting
    
    // ========== AUTO RENEWAL ==========
    @Column(nullable = false)
    private Boolean autoRenew = true; // Tự động gia hạn
    
    private LocalDateTime nextBillingDate; // Ngày billing tiếp theo
    
    // ========== CANCELLATION ==========
    private LocalDateTime cancelledAt; // Thời gian hủy
    private UUID cancelledBy; // User ID người hủy
    private String cancellationReason; // Lý do hủy
    
    // ========== PAYMENT TRACKING ==========
    @Column(length = 200)
    private String transactionCode; // Current active transaction code (for SePay)
    
    private String paymentMethod; // CREDIT_CARD, BANK_TRANSFER, E_WALLET
    private String lastPaymentId; // ID của payment transaction cuối cùng
    private LocalDateTime lastPaymentDate;
    
    @Column(length = 100)
    private String paymentGateway; // SEPAY, VNPAY, etc.
    
    // ========== AUDIT FIELDS ==========
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private UUID createdBy; // Admin UUID created subscription
    
    private LocalDateTime updatedAt;
    
    private UUID updatedBy; // Admin UUID updated subscription
    
    // ========== NOTES ==========
    @Column(length = 1000)
    private String notes; // Ghi chú từ admin
}

package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Subscription Plan Template - Managed by Super Admin
 * Defines available subscription tiers with their pricing and limits
 */
@Entity
@Table(name = "subscription_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String code; // TRIAL, STARTER, STANDARD, ENTERPRISE
    
    @Column(nullable = false, length = 100)
    private String name; // e.g., "Gói Khởi Đầu"
    
    @Column(length = 500)
    private String description;
    
    // Pricing
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyPrice;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal quarterlyPrice;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal yearlyPrice;
    
    @Column(length = 10)
    private String currency = "VND";
    
    // Usage Limits
    @Column(nullable = false)
    private Integer maxUsers;
    
    @Column(nullable = false)
    private Integer maxDocuments;
    
    @Column(nullable = false)
    private Integer maxStorageGb;
    
    @Column(nullable = false)
    private Integer maxApiCalls;
    
    @Column(nullable = false)
    private Integer maxChatbotRequests;
    
    @Column(nullable = false)
    private Integer maxRagDocuments;
    
    @Column(nullable = false)
    private Integer maxAiTokens;
    
    @Column(nullable = false)
    private Integer contextWindowTokens;
    
    @Column(nullable = false)
    private Integer ragChunkSize;
    
    @Column(length = 100)
    private String aiModel;
    
    @Column(length = 100)
    private String embeddingModel;
    
    @Column(nullable = false)
    private Boolean enableRag = false;
    
    // Trial specific
    @Column(nullable = false)
    private Boolean isTrial = false;
    
    @Column
    private Integer trialDays; // null if not trial
    
    // Status
    @Column(nullable = false)
    private Boolean isActive = true;
    
    @Column(nullable = false)
    private Integer displayOrder = 0; // For sorting in UI
    
    @Column(length = 500)
    private String features; // JSON or comma-separated list of features
    
    // Audit fields
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column
    private UUID createdBy;
    
    @Column
    private UUID updatedBy;
}

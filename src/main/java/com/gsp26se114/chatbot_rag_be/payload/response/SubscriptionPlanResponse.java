package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlanResponse {
    
    private UUID id;
    private String code;
    private String name;
    private String description;
    
    // Pricing
    private BigDecimal monthlyPrice;
    private BigDecimal quarterlyPrice;
    private BigDecimal yearlyPrice;
    private String currency;
    
    // Limits
    private Integer maxUsers;
    private Integer maxDocuments;
    private Integer maxStorageGb;
    private Integer maxApiCalls;
    private Integer maxChatbotRequests;
    private Integer maxRagDocuments;
    private Integer maxAiTokens;
    private Integer contextWindowTokens;
    private Integer ragChunkSize;
    private String aiModel;
    private String embeddingModel;
    
    // Status
    private Boolean isActive;
    private Integer displayOrder;
    private String features;
    
    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

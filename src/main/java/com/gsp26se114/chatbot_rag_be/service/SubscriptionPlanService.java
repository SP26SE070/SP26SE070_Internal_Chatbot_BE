package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.SubscriptionPlan;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateSubscriptionPlanRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateSubscriptionPlanRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.SubscriptionPlanResponse;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {
    
    private final SubscriptionPlanRepository planRepository;
    
    /**
     * Get all subscription plans (for admin)
     */
    public List<SubscriptionPlanResponse> getAllPlans() {
        log.info("Getting all subscription plans");
        return planRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get active plans (for tenant selection)
     */
    public List<SubscriptionPlanResponse> getActivePlans() {
        log.info("Getting active subscription plans");
        return planRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get plan by ID
     */
    public SubscriptionPlanResponse getPlanById(UUID id) {
        log.info("Getting plan by ID: {}", id);
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + id));
        return toResponse(plan);
    }
    
    /**
     * Get plan by code
     */
    public SubscriptionPlan getPlanByCode(String code) {
        return planRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + code));
    }
    
    /**
     * Create new subscription plan (Super Admin only)
     */
    @Transactional
    public SubscriptionPlanResponse createPlan(CreateSubscriptionPlanRequest request, UUID adminId) {
        log.info("Creating subscription plan: {}", request.getCode());
        
        // Check duplicate code
        if (planRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Plan code already exists: " + request.getCode());
        }
        
        // Validate trial plan
        if (request.getIsTrial() && request.getTrialDays() == null) {
            throw new IllegalArgumentException("Trial plan must have trialDays specified");
        }
        
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode(request.getCode().toUpperCase());
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        
        // Pricing
        plan.setMonthlyPrice(request.getMonthlyPrice());
        plan.setQuarterlyPrice(request.getQuarterlyPrice());
        plan.setYearlyPrice(request.getYearlyPrice());
        plan.setCurrency("VND");
        
        // Limits
        plan.setMaxUsers(request.getMaxUsers());
        plan.setMaxDocuments(request.getMaxDocuments());
        plan.setMaxStorageGb(request.getMaxStorageGb());
        plan.setMaxApiCalls(request.getMaxApiCalls());
        plan.setMaxChatbotRequests(request.getMaxChatbotRequests());
        plan.setMaxRagDocuments(request.getMaxRagDocuments());
        plan.setMaxAiTokens(request.getMaxAiTokens());
        plan.setContextWindowTokens(request.getContextWindowTokens());
        plan.setRagChunkSize(request.getRagChunkSize());
        
        // AI Config
        plan.setAiModel(request.getAiModel());
        plan.setEmbeddingModel(request.getEmbeddingModel());
        plan.setEnableRag(request.getEnableRag());
        
        // Trial
        plan.setIsTrial(request.getIsTrial());
        plan.setTrialDays(request.getTrialDays());
        
        // Status
        plan.setIsActive(true);
        plan.setDisplayOrder(request.getDisplayOrder());
        plan.setFeatures(request.getFeatures());
        
        // Audit
        plan.setCreatedBy(adminId);
        
        SubscriptionPlan saved = planRepository.save(plan);
        log.info("Created plan: {} (ID: {})", saved.getCode(), saved.getId());
        
        return toResponse(saved);
    }
    
    /**
     * Update subscription plan
     */
    @Transactional
    public SubscriptionPlanResponse updatePlan(UUID id, UpdateSubscriptionPlanRequest request, UUID adminId) {
        log.info("Updating subscription plan: {}", id);
        
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + id));
        
        // Update fields
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        
        // Pricing
        plan.setMonthlyPrice(request.getMonthlyPrice());
        plan.setQuarterlyPrice(request.getQuarterlyPrice());
        plan.setYearlyPrice(request.getYearlyPrice());
        
        // Limits
        plan.setMaxUsers(request.getMaxUsers());
        plan.setMaxDocuments(request.getMaxDocuments());
        plan.setMaxStorageGb(request.getMaxStorageGb());
        plan.setMaxApiCalls(request.getMaxApiCalls());
        plan.setMaxChatbotRequests(request.getMaxChatbotRequests());
        plan.setMaxRagDocuments(request.getMaxRagDocuments());
        plan.setMaxAiTokens(request.getMaxAiTokens());
        plan.setContextWindowTokens(request.getContextWindowTokens());
        plan.setRagChunkSize(request.getRagChunkSize());
        
        // AI Config
        plan.setAiModel(request.getAiModel());
        plan.setEmbeddingModel(request.getEmbeddingModel());
        plan.setEnableRag(request.getEnableRag());
        
        // Status
        plan.setIsActive(request.getIsActive());
        plan.setDisplayOrder(request.getDisplayOrder());
        plan.setFeatures(request.getFeatures());
        
        // Audit
        plan.setUpdatedBy(adminId);
        plan.setUpdatedAt(LocalDateTime.now());
        
        SubscriptionPlan updated = planRepository.save(plan);
        log.info("Updated plan: {}", updated.getCode());
        
        return toResponse(updated);
    }
    
    /**
     * Delete (deactivate) subscription plan
     */
    @Transactional
    public void deletePlan(UUID id, UUID adminId) {
        log.info("Deleting subscription plan: {}", id);
        
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + id));
        
        plan.setIsActive(false);
        plan.setUpdatedBy(adminId);
        plan.setUpdatedAt(LocalDateTime.now());
        
        planRepository.save(plan);
        log.info("Deactivated plan: {}", plan.getCode());
    }
    
    /**
     * Convert entity to response
     */
    private SubscriptionPlanResponse toResponse(SubscriptionPlan plan) {
        return SubscriptionPlanResponse.builder()
                .id(plan.getId())
                .code(plan.getCode())
                .name(plan.getName())
                .description(plan.getDescription())
                .monthlyPrice(plan.getMonthlyPrice())
                .quarterlyPrice(plan.getQuarterlyPrice())
                .yearlyPrice(plan.getYearlyPrice())
                .currency(plan.getCurrency())
                .maxUsers(plan.getMaxUsers())
                .maxDocuments(plan.getMaxDocuments())
                .maxStorageGb(plan.getMaxStorageGb())
                .maxApiCalls(plan.getMaxApiCalls())
                .maxChatbotRequests(plan.getMaxChatbotRequests())
                .maxRagDocuments(plan.getMaxRagDocuments())
                .maxAiTokens(plan.getMaxAiTokens())
                .contextWindowTokens(plan.getContextWindowTokens())
                .ragChunkSize(plan.getRagChunkSize())
                .aiModel(plan.getAiModel())
                .embeddingModel(plan.getEmbeddingModel())
                .enableRag(plan.getEnableRag())
                .isTrial(plan.getIsTrial())
                .trialDays(plan.getTrialDays())
                .isActive(plan.getIsActive())
                .displayOrder(plan.getDisplayOrder())
                .features(plan.getFeatures())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}

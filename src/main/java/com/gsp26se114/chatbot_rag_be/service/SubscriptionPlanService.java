package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.SubscriptionPlan;
import com.gsp26se114.chatbot_rag_be.entity.SubscriptionTier;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateSubscriptionPlanRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateSubscriptionPlanRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.SubscriptionPlanResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.SubscriptionPlanTypeResponse;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {
    
    private final SubscriptionPlanRepository planRepository;
    private static final Map<SubscriptionTier, String> DEFAULT_PLAN_NAMES = Map.of(
            SubscriptionTier.TRIAL, "Goi dung thu",
            SubscriptionTier.STARTER, "Goi Khoi Dau",
            SubscriptionTier.STANDARD, "Goi Tieu Chuan",
            SubscriptionTier.ENTERPRISE, "Goi Doanh Nghiep"
    );
    
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
        return planRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + code));
    }

    /**
     * Get available fixed plan types for frontend dropdown.
     */
    public List<SubscriptionPlanTypeResponse> getPlanTypes() {
        return Arrays.stream(SubscriptionTier.values())
                .map(tier -> SubscriptionPlanTypeResponse.builder()
                        .code(tier.name())
                        .defaultName(DEFAULT_PLAN_NAMES.get(tier))
                        .build())
                .toList();
    }
    
    /**
     * Create new subscription plan (Super Admin only)
     */
    @Transactional
    public SubscriptionPlanResponse createPlan(CreateSubscriptionPlanRequest request, UUID adminId) {
        String normalizedCode = request.getPlanType().name();
        log.info("Creating subscription plan with type: {}", normalizedCode);
        
        // Check duplicate code
        if (planRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Plan code already exists: " + normalizedCode);
        }
        
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode(normalizedCode);
        String requestName = request.getName();
        if (requestName == null || requestName.isBlank()) {
            plan.setName(DEFAULT_PLAN_NAMES.get(request.getPlanType()));
        } else {
            plan.setName(requestName.trim());
        }
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
                .isActive(plan.getIsActive())
                .displayOrder(plan.getDisplayOrder())
                .features(plan.getFeatures())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}

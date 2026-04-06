package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.payload.request.CreateSubscriptionPlanRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateSubscriptionPlanRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.SubscriptionPlanResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.SubscriptionPlanTypeResponse;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.SubscriptionPlanService;
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

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/subscription-plans")
@RequiredArgsConstructor
@Tag(name = "04. 🛡️ Super Admin - Subscription Plan Management", description = "Quản lý các gói subscription (SUPER_ADMIN)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminSubscriptionPlanController {
    
    private final SubscriptionPlanService planService;
    
    /**
     * Get all subscription plans
     */
    @GetMapping
    @Operation(summary = "Lấy tất cả subscription plans", 
               description = "Lấy danh sách tất cả plans (cả active và inactive)")
    public ResponseEntity<List<SubscriptionPlanResponse>> getAllPlans() {
        log.info("Admin getting all subscription plans");
        List<SubscriptionPlanResponse> plans = planService.getAllPlans();
        return ResponseEntity.ok(plans);
    }
    
    /**
     * Get active subscription plans
     */
    @GetMapping("/active")
    @Operation(summary = "Lấy các plan đang active", 
               description = "Lấy danh sách plans đang active, sắp xếp theo display order")
    public ResponseEntity<List<SubscriptionPlanResponse>> getActivePlans() {
        log.info("Getting active subscription plans");
        List<SubscriptionPlanResponse> plans = planService.getActivePlans();
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/types")
    @Operation(summary = "Lấy danh sách loại plan cố định",
            description = "Trả về các code plan cố định cho dropdown FE: TRIAL, STARTER, STANDARD, ENTERPRISE")
    public ResponseEntity<List<SubscriptionPlanTypeResponse>> getPlanTypes() {
        return ResponseEntity.ok(planService.getPlanTypes());
    }
    
    /**
     * Get plan by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết plan", 
               description = "Lấy thông tin chi tiết của plan theo ID")
    public ResponseEntity<SubscriptionPlanResponse> getPlanById(@PathVariable UUID id) {
        log.info("Getting plan: {}", id);
        SubscriptionPlanResponse plan = planService.getPlanById(id);
        return ResponseEntity.ok(plan);
    }
    
    /**
     * Create new subscription plan
     */
    @PostMapping
    @Operation(summary = "Tạo plan mới", 
               description = "Tạo subscription plan mới (SUPER_ADMIN only)")
    public ResponseEntity<SubscriptionPlanResponse> createPlan(
            @Valid @RequestBody CreateSubscriptionPlanRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Creating new subscription plan type: {}", request.getPlanType());
        SubscriptionPlanResponse plan = planService.createPlan(request, userPrincipal.getId());
        return ResponseEntity.ok(plan);
    }
    
    /**
     * Update subscription plan
     */
    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật plan", 
               description = "Cập nhật thông tin subscription plan")
    public ResponseEntity<SubscriptionPlanResponse> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubscriptionPlanRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Updating subscription plan: {}", id);
        SubscriptionPlanResponse plan = planService.updatePlan(id, request, userPrincipal.getId());
        return ResponseEntity.ok(plan);
    }
    
    @PutMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate plan",
               description = "Soft disable subscription plan bằng cách set isActive = false")
    public ResponseEntity<SubscriptionPlanResponse> deactivatePlan(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Deactivating subscription plan: {}", id);
        SubscriptionPlanResponse plan = planService.deactivatePlan(id, userPrincipal.getId());
        return ResponseEntity.ok(plan);
    }

    @PutMapping("/{id}/activate")
    @Operation(summary = "Activate plan",
               description = "Activate subscription plan bằng cách set isActive = true")
    public ResponseEntity<SubscriptionPlanResponse> activatePlan(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Activating subscription plan: {}", id);
        SubscriptionPlanResponse plan = planService.activatePlan(id, userPrincipal.getId());
        return ResponseEntity.ok(plan);
    }

    /**
     * Hard delete subscription plan
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Hard delete plan",
               description = "Xóa vĩnh viễn subscription plan")
    public ResponseEntity<MessageResponse> deletePlan(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Hard deleting subscription plan: {}", id);
        planService.hardDeletePlan(id, userPrincipal.getId());
        return ResponseEntity.ok(new MessageResponse("Plan deleted successfully"));
    }
}

package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.payload.request.CreateSubscriptionPlanRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateSubscriptionPlanRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.SubscriptionPlanResponse;
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
        log.info("Creating new subscription plan: {}", request.getCode());
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
    
    /**
     * Delete (deactivate) subscription plan
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa plan", 
               description = "Deactivate subscription plan (không xóa vật lý)")
    public ResponseEntity<MessageResponse> deletePlan(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Deleting subscription plan: {}", id);
        planService.deletePlan(id, userPrincipal.getId());
        return ResponseEntity.ok(new MessageResponse("Plan đã được deactivate thành công"));
    }
}

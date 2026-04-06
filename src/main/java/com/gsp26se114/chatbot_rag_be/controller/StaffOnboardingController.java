package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.payload.request.CreateOnboardingModuleRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateOnboardingModuleRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.OnboardingModuleResponse;
import com.gsp26se114.chatbot_rag_be.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/onboarding")
@RequiredArgsConstructor
@Tag(name = "18. 🧠 Staff - Onboarding Content", description = "Quản lý onboarding content theo tenant (STAFF)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('STAFF')")
public class StaffOnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping("/tenants/{tenantId}/modules")
    @Operation(summary = "Lấy danh sách onboarding modules theo tenant")
    public ResponseEntity<List<OnboardingModuleResponse>> getTenantModules(
            @PathVariable UUID tenantId,
            @RequestParam(value = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(onboardingService.getModulesForTenant(tenantId, includeInactive));
    }

    @PostMapping("/tenants/{tenantId}/modules")
    @Operation(summary = "Tạo onboarding module theo tenant")
    public ResponseEntity<OnboardingModuleResponse> createModule(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateOnboardingModuleRequest request) {
        OnboardingModuleResponse module = onboardingService
                .createModuleForTenantByStaff(userDetails.getUsername(), tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(module);
    }

    @PutMapping("/tenants/{tenantId}/modules/{moduleId}")
    @Operation(summary = "Cập nhật onboarding module theo tenant")
    public ResponseEntity<OnboardingModuleResponse> updateModule(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID tenantId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody UpdateOnboardingModuleRequest request) {
        return ResponseEntity.ok(onboardingService.updateModuleForTenantByStaff(
                userDetails.getUsername(), tenantId, moduleId, request));
    }

    @DeleteMapping("/tenants/{tenantId}/modules/{moduleId}")
    @Operation(summary = "Vô hiệu hóa onboarding module theo tenant")
    public ResponseEntity<MessageResponse> deactivateModule(
            @PathVariable UUID tenantId,
            @PathVariable UUID moduleId) {
        onboardingService.deactivateModuleForTenantByStaff(tenantId, moduleId);
        return ResponseEntity.ok(new MessageResponse("Onboarding module đã được vô hiệu hóa"));
    }

    @PostMapping(value = "/tenants/{tenantId}/modules/{moduleId}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload file chi tiết onboarding (.txt/.pdf) theo tenant")
    public ResponseEntity<OnboardingModuleResponse> uploadModuleAttachment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID tenantId,
            @PathVariable UUID moduleId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(onboardingService.uploadModuleAttachmentForTenantByStaff(
                userDetails.getUsername(), tenantId, moduleId, file));
    }
}

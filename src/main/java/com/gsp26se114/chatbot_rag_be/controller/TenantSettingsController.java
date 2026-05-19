package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.payload.request.UpdateTenantProfileRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.TenantResponse;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.TenantSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/tenant-admin/tenant")
@RequiredArgsConstructor
@Tag(name = "17. Tenant Admin - Tenant Settings", description = "Tenant profile and branding settings")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TenantSettingsController {

    private final TenantSettingsService tenantSettingsService;

    @PutMapping("/profile")
    @Operation(summary = "Update tenant profile", description = "Update tenant address, website, and company size")
    public ResponseEntity<TenantResponse> updateTenantProfile(
        @Valid @RequestBody UpdateTenantProfileRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        TenantResponse response = tenantSettingsService.updateTenantProfile(principal.getTenantId(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logo")
    @Operation(summary = "Upload tenant logo", description = "Upload tenant logo image and update logo URL")
    public ResponseEntity<Map<String, String>> uploadTenantLogo(
        @RequestParam("file") MultipartFile file,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        TenantResponse response = tenantSettingsService.updateTenantLogo(principal.getTenantId(), file);
        return ResponseEntity.ok(Map.of("logoUrl", response.getLogoUrl()));
    }
}

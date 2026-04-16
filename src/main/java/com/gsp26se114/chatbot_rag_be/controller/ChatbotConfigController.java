package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.ChatbotConfig;
import com.gsp26se114.chatbot_rag_be.payload.response.ChatbotConfigResponse;
import com.gsp26se114.chatbot_rag_be.repository.ChatbotConfigRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/tenant-admin/chatbot")
@RequiredArgsConstructor
public class ChatbotConfigController {

    private final ChatbotConfigRepository chatbotConfigRepository;

    @GetMapping("/config")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get chatbot config for current tenant")
    public ResponseEntity<ChatbotConfigResponse> getConfig(
            @AuthenticationPrincipal UserPrincipal userDetails) {

        ChatbotConfig config = chatbotConfigRepository
                .findByTenantId(userDetails.getTenantId())
                .orElse(null);

        String mode = (config != null && config.getMode() != null)
                ? config.getMode() : "BALANCED";

        return ResponseEntity.ok(new ChatbotConfigResponse(mode));
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update chatbot mode for current tenant")
    public ResponseEntity<ChatbotConfigResponse> updateConfig(
            @AuthenticationPrincipal UserPrincipal userDetails,
            @RequestBody Map<String, String> body) {

        String newMode = body.get("mode");
        if (newMode == null || !newMode.matches("BALANCED|STRICT|FLEXIBLE")) {
            return ResponseEntity.badRequest().build();
        }

        ChatbotConfig config = chatbotConfigRepository
                .findByTenantId(userDetails.getTenantId())
                .orElseGet(() -> {
                    ChatbotConfig newConfig = new ChatbotConfig();
                    newConfig.setTenantId(userDetails.getTenantId());
                    return newConfig;
                });

        config.setMode(newMode.toUpperCase());
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(userDetails.getId());
        chatbotConfigRepository.save(config);

        log.info("Tenant {} updated chatbot mode to {}", userDetails.getTenantId(), newMode);
        return ResponseEntity.ok(new ChatbotConfigResponse(newMode));
    }
}
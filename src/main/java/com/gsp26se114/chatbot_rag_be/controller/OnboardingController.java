package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.payload.request.UpdateOnboardingProgressRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.OnboardingMyOverviewResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.OnboardingProgressResponse;
import com.gsp26se114.chatbot_rag_be.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
@Tag(name = "17. 📖 Employee Onboarding", description = "Theo dõi tiến độ onboarding cho user trong tenant")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping("/me")
    @Operation(summary = "Lấy trạng thái onboarding của user hiện tại")
    public ResponseEntity<OnboardingMyOverviewResponse> getMyOnboarding(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(onboardingService.getMyOnboarding(userDetails.getUsername()));
    }

    @PutMapping("/modules/{moduleId}/progress")
    @Operation(summary = "Cập nhật phần trăm đã đọc của onboarding module")
    public ResponseEntity<OnboardingProgressResponse> updateMyProgress(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID moduleId,
            @Valid @RequestBody UpdateOnboardingProgressRequest request) {
        return ResponseEntity.ok(onboardingService.updateMyProgress(userDetails.getUsername(), moduleId, request));
    }

    @PostMapping("/modules/{moduleId}/complete")
    @Operation(summary = "Đánh dấu hoàn thành onboarding module")
    public ResponseEntity<OnboardingProgressResponse> markMyModuleComplete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID moduleId) {
        return ResponseEntity.ok(onboardingService.markMyModuleCompleted(userDetails.getUsername(), moduleId));
    }

    @GetMapping("/modules/{moduleId}/attachment")
    @Operation(summary = "Lấy file chi tiết onboarding module của user hiện tại")
    public ResponseEntity<byte[]> getMyModuleAttachment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID moduleId) {
        OnboardingService.OnboardingAttachmentData attachment =
                onboardingService.getMyModuleAttachment(userDetails.getUsername(), moduleId);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(attachment.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentLength(attachment.content().length);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(attachment.fileName(), StandardCharsets.UTF_8)
                .build());

        return new ResponseEntity<>(attachment.content(), headers, HttpStatus.OK);
    }
}

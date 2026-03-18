package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.DocumentTag;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateDocumentTagRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateDocumentTagRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.DocumentTagResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.repository.DocumentTagRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge/tags")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "20. 🏷️ Document Tags", description = "Quản lý bộ tag chuẩn cho tài liệu")
public class DocumentTagController {

    private final DocumentTagRepository documentTagRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Danh sách tag đang active của tenant")
    public ResponseEntity<List<DocumentTagResponse>> listActiveTags(
            @AuthenticationPrincipal UserPrincipal user) {

        return ResponseEntity.ok(documentTagRepository.findByTenantIdAndIsActiveTrueOrderByNameAsc(user.getTenantId())
                .stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/manage")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Danh sách toàn bộ tag của tenant để quản trị")
    public ResponseEntity<List<DocumentTagResponse>> listAllTags(
            @AuthenticationPrincipal UserPrincipal user) {

        return ResponseEntity.ok(documentTagRepository.findByTenantIdOrderByNameAsc(user.getTenantId())
                .stream()
                .map(this::toResponse)
                .toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Tạo tag mới cho tenant")
    public ResponseEntity<?> createTag(
            @Valid @RequestBody CreateDocumentTagRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        String normalizedCode = normalizeCode(request.code());
        if (normalizedCode.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Code tag không hợp lệ"));
        }
        if (documentTagRepository.existsByTenantIdAndCode(user.getTenantId(), normalizedCode)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Code tag đã tồn tại trong tenant"));
        }

        DocumentTag tag = new DocumentTag();
        tag.setTenantId(user.getTenantId());
        tag.setName(request.name().trim());
        tag.setCode(normalizedCode);
        tag.setDescription(trimToNull(request.description()));
        tag.setIsActive(true);
        tag.setCreatedBy(user.getId());
        tag.setCreatedAt(LocalDateTime.now());

        tag = documentTagRepository.save(tag);
        log.info("Tenant {} created document tag {}", user.getTenantId(), tag.getCode());
        return ResponseEntity.ok(toResponse(tag));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật tag của tenant")
    public ResponseEntity<?> updateTag(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentTagRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        DocumentTag tag = documentTagRepository.findByIdAndTenantId(id, user.getTenantId())
                .orElse(null);
        if (tag == null) {
            return ResponseEntity.notFound().build();
        }

        String normalizedCode = normalizeCode(request.code());
        if (normalizedCode.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Code tag không hợp lệ"));
        }
        if (documentTagRepository.existsByTenantIdAndCodeAndIdNot(user.getTenantId(), normalizedCode, id)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Code tag đã tồn tại trong tenant"));
        }

        tag.setName(request.name().trim());
        tag.setCode(normalizedCode);
        tag.setDescription(trimToNull(request.description()));
        if (request.isActive() != null) {
            tag.setIsActive(request.isActive());
        }
        tag.setUpdatedAt(LocalDateTime.now());

        tag = documentTagRepository.save(tag);
        log.info("Tenant {} updated document tag {}", user.getTenantId(), tag.getCode());
        return ResponseEntity.ok(toResponse(tag));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Vô hiệu hóa tag của tenant")
    public ResponseEntity<?> deactivateTag(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {

        DocumentTag tag = documentTagRepository.findByIdAndTenantId(id, user.getTenantId())
                .orElse(null);
        if (tag == null) {
            return ResponseEntity.notFound().build();
        }

        tag.setIsActive(false);
        tag.setUpdatedAt(LocalDateTime.now());
        documentTagRepository.save(tag);
        log.info("Tenant {} deactivated document tag {}", user.getTenantId(), tag.getCode());
        return ResponseEntity.ok(new MessageResponse("Tag đã được vô hiệu hóa"));
    }

    private DocumentTagResponse toResponse(DocumentTag tag) {
        return DocumentTagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .code(tag.getCode())
                .description(tag.getDescription())
                .isActive(tag.getIsActive())
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .build();
    }

    private String normalizeCode(String code) {
        return (code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
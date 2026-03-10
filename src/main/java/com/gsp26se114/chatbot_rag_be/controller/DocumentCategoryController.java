package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.DocumentCategory;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateDocumentCategoryRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateDocumentCategoryRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.DocumentCategoryResponse;
import com.gsp26se114.chatbot_rag_be.repository.DocumentCategoryRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD + cây phân cấp cho Document Categories (per-tenant).
 *
 * Cấu trúc cây ví dụ:
 *   HR (root)
 *   ├── Onboarding
 *   │   └── New Hire Checklist
 *   └── Policies
 *       └── Leave Policy
 */
@RestController
@RequestMapping("/api/v1/knowledge/categories")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "18. 🗂️ Document Categories", description = "Quản lý phân loại tài liệu theo cấu trúc cây")
public class DocumentCategoryController {

    private final DocumentCategoryRepository categoryRepository;

    // =========================================================
    // HELPER: map entity → response (không bao gồm children)
    // =========================================================
    private DocumentCategoryResponse toResponse(DocumentCategory c) {
        return DocumentCategoryResponse.builder()
                .id(c.getId())
                .tenantId(c.getTenantId())
                .parentId(c.getParentId())
                .name(c.getName())
                .code(c.getCode())
                .description(c.getDescription())
                .isActive(c.getIsActive())
                .createdBy(c.getCreatedBy())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    // =========================================================
    // GET /tree  — Trả về cây đầy đủ của tenant
    // =========================================================
    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_USER')")
    @Operation(
        summary = "Lấy cây phân loại tài liệu",
        description = "Trả về danh sách root-category, mỗi node có `children` lồng vào nhau."
    )
    public ResponseEntity<List<DocumentCategoryResponse>> getTree(
            @AuthenticationPrincipal UserPrincipal user) {

        List<DocumentCategory> all = categoryRepository
                .findByTenantIdAndIsActiveTrueOrderByNameAsc(user.getTenantId());

        // Nhóm theo parentId
        Map<UUID, List<DocumentCategoryResponse>> byParent = all.stream()
                .map(this::toResponse)
                .collect(Collectors.groupingBy(
                        r -> r.getParentId() == null ? UUID.fromString("00000000-0000-0000-0000-000000000000") : r.getParentId()
                ));

        UUID rootSentinel = UUID.fromString("00000000-0000-0000-0000-000000000000");

        // Gán children đệ quy
        byParent.values().forEach(nodes ->
            nodes.forEach(node -> node.setChildren(byParent.get(node.getId())))
        );

        List<DocumentCategoryResponse> roots = byParent.getOrDefault(rootSentinel, List.of());
        return ResponseEntity.ok(roots);
    }

    // =========================================================
    // GET /  — Flat list (tất cả categories, không lồng cây)
    // =========================================================
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_USER')")
    @Operation(summary = "Flat list tất cả categories của tenant")
    public ResponseEntity<List<DocumentCategoryResponse>> listFlat(
            @AuthenticationPrincipal UserPrincipal user) {

        List<DocumentCategoryResponse> result = categoryRepository
                .findByTenantIdAndIsActiveTrueOrderByNameAsc(user.getTenantId())
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    // =========================================================
    // GET /{id}  — Chi tiết một category
    // =========================================================
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_USER')")
    @Operation(summary = "Lấy chi tiết một category")
    public ResponseEntity<?> getOne(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {

        return categoryRepository.findById(id)
                .filter(c -> c.getTenantId().equals(user.getTenantId()))
                .map(c -> ResponseEntity.ok(toResponse(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================
    // GET /{id}/children  — Sub-categories trực tiếp
    // =========================================================
    @GetMapping("/{id}/children")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_USER')")
    @Operation(summary = "Lấy danh sách sub-category trực tiếp của một category")
    public ResponseEntity<List<DocumentCategoryResponse>> getChildren(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {

        List<DocumentCategoryResponse> result = categoryRepository
                .findByTenantIdAndParentIdAndIsActiveTrueOrderByNameAsc(user.getTenantId(), id)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    // =========================================================
    // POST /  — Tạo category mới
    // =========================================================
    @PostMapping
    @PreAuthorize("hasPermission('MANAGE_KNOWLEDGE_BASE')")
    @Operation(
        summary = "Tạo category mới",
        description = "parentId = null → category cấp root. Code phải unique trong phạm vi tenant."
    )
    public ResponseEntity<?> create(
            @Valid @RequestBody CreateDocumentCategoryRequest req,
            @AuthenticationPrincipal UserPrincipal user) {

        // Validate code unique trong tenant
        if (categoryRepository.existsByTenantIdAndCode(user.getTenantId(), req.code())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Code '" + req.code() + "' đã tồn tại trong tenant này"));
        }

        // Validate parent tồn tại và cùng tenant
        if (req.parentId() != null) {
            boolean parentValid = categoryRepository.findById(req.parentId())
                    .map(p -> p.getTenantId().equals(user.getTenantId()) && p.getIsActive())
                    .orElse(false);
            if (!parentValid) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Parent category không hợp lệ hoặc không active"));
            }
        }

        DocumentCategory cat = new DocumentCategory();
        cat.setTenantId(user.getTenantId());
        cat.setParentId(req.parentId());
        cat.setName(req.name());
        cat.setCode(req.code().toUpperCase().trim());
        cat.setDescription(req.description());
        cat.setIsActive(true);
        cat.setCreatedBy(user.getId());
        cat.setCreatedAt(LocalDateTime.now());

        cat = categoryRepository.save(cat);
        log.info("Created document category: {} ({}) tenant={}", cat.getName(), cat.getCode(), cat.getTenantId());

        return ResponseEntity.ok(toResponse(cat));
    }

    // =========================================================
    // PUT /{id}  — Cập nhật category
    // =========================================================
    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('MANAGE_KNOWLEDGE_BASE')")
    @Operation(summary = "Cập nhật thông tin category")
    public ResponseEntity<?> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentCategoryRequest req,
            @AuthenticationPrincipal UserPrincipal user) {

        DocumentCategory cat = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy category"));

        if (!cat.getTenantId().equals(user.getTenantId())) {
            return ResponseEntity.status(403).body(Map.of("message", "Không có quyền chỉnh sửa category này"));
        }

        // Kiểm tra code unique (loại trừ chính nó)
        if (categoryRepository.existsByTenantIdAndCodeAndIdNot(user.getTenantId(), req.code(), id)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Code '" + req.code() + "' đã tồn tại trong tenant này"));
        }

        // Không được set parent thành chính nó
        if (id.equals(req.parentId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Category không thể là parent của chính nó"));
        }

        // Validate parent (nếu thay đổi)
        if (req.parentId() != null) {
            boolean parentValid = categoryRepository.findById(req.parentId())
                    .map(p -> p.getTenantId().equals(user.getTenantId()) && p.getIsActive())
                    .orElse(false);
            if (!parentValid) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Parent category không hợp lệ hoặc không active"));
            }
        }

        cat.setName(req.name());
        cat.setCode(req.code().toUpperCase().trim());
        cat.setDescription(req.description());
        cat.setParentId(req.parentId());
        if (req.isActive() != null) cat.setIsActive(req.isActive());
        cat.setUpdatedAt(LocalDateTime.now());

        categoryRepository.save(cat);
        return ResponseEntity.ok(toResponse(cat));
    }

    // =========================================================
    // DELETE /{id}  — Soft-delete (deactivate)
    // =========================================================
    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('MANAGE_KNOWLEDGE_BASE')")
    @Operation(
        summary = "Vô hiệu hóa category (soft delete)",
        description = "Không xóa vật lý. Trả lỗi nếu category còn sub-category đang active."
    )
    public ResponseEntity<?> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {

        DocumentCategory cat = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy category"));

        if (!cat.getTenantId().equals(user.getTenantId())) {
            return ResponseEntity.status(403).body(Map.of("message", "Không có quyền xóa category này"));
        }

        if (categoryRepository.hasActiveChildren(id)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Không thể xóa category còn sub-category đang active. Hãy xóa sub-category trước."));
        }

        cat.setIsActive(false);
        cat.setUpdatedAt(LocalDateTime.now());
        categoryRepository.save(cat);

        log.info("Deactivated document category: {} tenant={}", id, user.getTenantId());
        return ResponseEntity.ok(Map.of("message", "Đã vô hiệu hóa category thành công"));
    }
}

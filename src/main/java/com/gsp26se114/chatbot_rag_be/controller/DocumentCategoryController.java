package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.DocumentCategory;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateDocumentCategoryRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateDocumentCategoryRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.DocumentCategoryResponse;
import com.gsp26se114.chatbot_rag_be.repository.DocumentCategoryRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Cây phân loại tài liệu",
        description = """
            Trả về tất cả categories đang active của tenant dưới dạng **cây lồng nhau**.

            Mỗi node có `children` chứa các sub-category trực tiếp.

            Ví dụ kết quả:
            ```json
            [
              {
                "id": "...", "name": "HR", "code": "HR",
                "children": [
                  { "id": "...", "name": "Onboarding", "code": "HR_ONBOARDING", "children": [] },
                  { "id": "...", "name": "Policies", "code": "HR_POLICY", "children": [] }
                ]
              }
            ]
            ```
            """
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
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Flat list tất cả categories của tenant",
        description = """
            Trả về danh sách phẳng (không lồng cây) tất cả categories đang active, sắp xếp theo tên A→Z.
            Bao gồm cả root và sub-categories, mỗi phần tử có `parentId` để biết vị trí trong cây.

            Dùng cho dropdown chọn category khi upload tài liệu.
            """
    )
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
    @GetMapping("/detail/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Xem chi tiết một category",
        description = "Trả về thông tin đầy đủ của category bao gồm `id`, `name`, `code`, `description`, `parentId`, `isActive`, `createdAt`."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thông tin category"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy hoặc thuộc tenant khác")
    })
    public ResponseEntity<?> getOne(
            @Parameter(description = "ID của category", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal user) {

        return categoryRepository.findById(id)
                .filter(c -> c.getTenantId().equals(user.getTenantId()))
                .map(c -> ResponseEntity.ok(toResponse(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================
    // GET /{id}/children  — Sub-categories trực tiếp
    // =========================================================
    @GetMapping("/children/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Sub-categories trực tiếp của một category",
        description = """
            Trả về danh sách các category con trực tiếp (chỉ độ sâu 1) của category chỉ định.
            Khác với `/tree`: API này **không lồng tiếp**, chỉ lấy con trực tiếp có `parentId = {id}`.

            Dùng cho lazy-loading từng nứt cây trên UI.
            """
    )
    public ResponseEntity<List<DocumentCategoryResponse>> getChildren(
            @Parameter(description = "ID của category cha", required = true) @PathVariable UUID id,
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
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('DOCUMENT_WRITE')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Tạo category mới",
        description = """
            Tạo một document category mới trong tenant.

            **Request body:**
            ```json
            {
              "name": "Nhân sự",
              "code": "HR",
              "description": "Tài liệu liên quan nhân sự",
              "parentId": null
            }
            ```

            - `parentId = null` → category cấp root
            - `parentId = <uuid>` → sub-category của category đó
            - `code` phải unique trong phạm vi tenant (không phân biệt hoa thường, tự động uppercase)
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tạo thành công, trả về DocumentCategoryResponse"),
        @ApiResponse(responseCode = "400", description = "Code đã tồn tại hoặc parent không hợp lệ")
    })
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
    @PutMapping("/update/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_WRITE')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Cập nhật thông tin category",
        description = """
            Cập nhật `name`, `code`, `description`, `parentId`, `isActive` của category.

            **Validation:**
            - Không được set `parentId` thành chính ID của category **đó** (tự tham chiếu)
            - `code` mới phải unique trong tenant (ngoại trừ chính nó)
            - Nếu đổi `isActive = false`, cần xóa hết sub-category con trước (dùng DELETE từng cái)
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
        @ApiResponse(responseCode = "400", description = "Code trùng / self-parent / parent không hợp lệ"),
        @ApiResponse(responseCode = "403", description = "Không có quyền sửa category này"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy category")
    })
    public ResponseEntity<?> update(
            @Parameter(description = "ID của category cần cập nhật", required = true) @PathVariable UUID id,
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
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_WRITE')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Vô hiệu hóa category (soft delete)",
        description = """
            Đánh dấu `isActive = false`. **Không xóa vật lý** khỏi database.

            **Biểu kiện:** Category phải không còn sub-category nào đang active.
            Nếu còn, API sẽ trả `400` với hướng dẫn xóa sub-category trước.

            Tài liệu đang gắn với category này vẫn tồn tại nhưng category sẽ không xuất hiện trong cây nữa.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vô hiệu hóa thành công"),
        @ApiResponse(responseCode = "400", description = "Còn sub-category đang active"),
        @ApiResponse(responseCode = "403", description = "Không có quyền xóa category này"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy category")
    })
    public ResponseEntity<?> delete(
            @Parameter(description = "ID của category cần vô hiệu hóa", required = true) @PathVariable UUID id,
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

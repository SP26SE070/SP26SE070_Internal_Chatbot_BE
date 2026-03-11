package com.gsp26se114.chatbot_rag_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsp26se114.chatbot_rag_be.entity.DocumentEntity;
import com.gsp26se114.chatbot_rag_be.entity.DocumentVersion;
import com.gsp26se114.chatbot_rag_be.entity.DocumentVisibility;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateDocumentAccessRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.DocumentResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.DocumentVersionResponse;
import com.gsp26se114.chatbot_rag_be.repository.DocumentCategoryRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentChunkRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentVersionRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.DocumentProcessingService;
import com.gsp26se114.chatbot_rag_be.service.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Controller for document upload and management in Knowledge Base
 */
@RestController
@RequestMapping("/api/v1/knowledge/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "17. 📚 Knowledge Base", description = "Document upload and management APIs")
public class DocumentController {

    private final MinioService minioService;
    private final DocumentRepository documentRepository;
    private final DocumentCategoryRepository documentCategoryRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentProcessingService documentProcessingService;
    private final ObjectMapper objectMapper;

    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/markdown",
            "text/csv"
    );

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    /** Convert DocumentEntity → DocumentResponse (full fields). */
    private DocumentResponse toResponse(DocumentEntity doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .originalFileName(doc.getOriginalFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .category(doc.getCategory())
                .categoryId(doc.getCategoryId())
                .description(doc.getDescription())
                .visibility(doc.getVisibility())
                .accessibleDepartments(doc.getAccessibleDepartments())
                .accessibleRoles(doc.getAccessibleRoles())
                .embeddingStatus(doc.getEmbeddingStatus())
                .chunkCount(doc.getChunkCount())
                .uploadedByName(doc.getUploadedByName())
                .uploadedAt(doc.getUploadedAt())
                .documentTitle(doc.getDocumentTitle())
                .versionNumber(doc.getVersionNumber())
                .versionNote(doc.getVersionNote())
                .build();
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_USER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Upload tài liệu vào Knowledge Base",
        description = """
            Upload file lên MinIO và kích hoạt embedding tự động (async).

            **File hỗ trợ:** PDF, DOCX, XLSX, PPTX, TXT, MD, CSV (tối đa 50MB)

            **visibility:**
            - `COMPANY_WIDE` — tất cả nhân viên trong tenant đều xài được (không cần accessibleDepartments / accessibleRoles)
            - `SPECIFIC_DEPARTMENTS` — chỉ phòng ban trong `accessibleDepartments` mới thấy
            - `SPECIFIC_ROLES` — chỉ role trong `accessibleRoles` mới thấy

            **embeddingStatus** sau upload: `PENDING` → hệ thống tự chuyển sang `COMPLETED` khi xưử lý xong.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Upload thành công, trả về DocumentResponse"),
        @ApiResponse(responseCode = "400", description = "File rỗng / vượt 50MB / loại file không hỗ trợ / visibility không hợp lệ"),
        @ApiResponse(responseCode = "500", description = "Lỗi server khi upload")
    })
    public ResponseEntity<?> uploadDocument(
            @Parameter(description = "File cần upload (PDF/DOCX/XLSX/PPTX/TXT/MD/CSV, tối đa 50MB)", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "UUID của document category (tùy chọn, lấy từ API /categories)")
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @Parameter(description = "Mô tả ngắn về tài liệu")
            @RequestParam(value = "description", required = false) String description,
            @Parameter(description = "Phạm vi hiển thị: COMPANY_WIDE | SPECIFIC_DEPARTMENTS | SPECIFIC_ROLES")
            @RequestParam(value = "visibility", defaultValue = "COMPANY_WIDE") DocumentVisibility visibility,
            @Parameter(description = "Danh sách department ID được xem (bắt buộc khi visibility = SPECIFIC_DEPARTMENTS)")
            @RequestParam(value = "accessibleDepartments", required = false) List<Integer> accessibleDepartments,
            @Parameter(description = "Danh sách role ID được xem (bắt buộc khi visibility = SPECIFIC_ROLES)")
            @RequestParam(value = "accessibleRoles", required = false) List<Integer> accessibleRoles,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }

            if (file.getSize() > MAX_FILE_SIZE) {
                return ResponseEntity.badRequest().body("File size exceeds 50MB limit");
            }

            String contentType = file.getContentType();
            if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
                return ResponseEntity.badRequest().body(
                    "Unsupported file type. Allowed: PDF, DOCX, XLSX, PPTX, TXT, MD, CSV"
                );
            }

            // Validate visibility settings
            if (visibility == DocumentVisibility.COMPANY_WIDE) {
                if ((accessibleDepartments != null && !accessibleDepartments.isEmpty()) ||
                    (accessibleRoles != null && !accessibleRoles.isEmpty())) {
                    return ResponseEntity.badRequest().body(
                        "Khi chọn COMPANY_WIDE, không được set accessibleDepartments hoặc accessibleRoles. Tài liệu sẽ được toàn công ty truy cập."
                    );
                }
            }
            
            if (visibility == DocumentVisibility.SPECIFIC_DEPARTMENTS) {
                if (accessibleDepartments == null || accessibleDepartments.isEmpty()) {
                    return ResponseEntity.badRequest().body(
                        "Khi chọn SPECIFIC_DEPARTMENTS, bạn phải chỉ định ít nhất 1 phòng ban trong accessibleDepartments"
                    );
                }
            }
            
            if (visibility == DocumentVisibility.SPECIFIC_ROLES) {
                if (accessibleRoles == null || accessibleRoles.isEmpty()) {
                    return ResponseEntity.badRequest().body(
                        "Khi chọn SPECIFIC_ROLES, bạn phải chỉ định ít nhất 1 role trong accessibleRoles"
                    );
                }
            }

            log.info("Uploading document: {} ({})", file.getOriginalFilename(), contentType);

            // Upload to MinIO
            String folder = "tenant-" + userDetails.getTenantId() + "/documents";
            String storagePath = minioService.uploadDocument(file, folder);
            
            // Extract filename from path (format: folder/uuid_originalname.ext)
            String fileName = storagePath.substring(storagePath.lastIndexOf('/') + 1);
            
            log.info("File uploaded to MinIO: {}", storagePath);

            // Save metadata to database
            DocumentEntity document = new DocumentEntity();
            document.setFileName(fileName);
            document.setOriginalFileName(file.getOriginalFilename());
            document.setFileType(contentType);
            document.setFileSize(file.getSize());
            document.setStoragePath(storagePath);
            document.setTenantId(userDetails.getTenantId());
            if (categoryId != null) {
                boolean categoryExists = documentCategoryRepository.findById(categoryId)
                        .map(c -> c.getTenantId().equals(userDetails.getTenantId()) && c.getIsActive())
                        .orElse(false);
                if (!categoryExists) {
                    return ResponseEntity.badRequest().body("Category không tồn tại hoặc không thuộc tenant này");
                }
                document.setCategoryId(categoryId);
            }
            document.setDescription(description);
            document.setVisibility(visibility);
            
            // Force null for COMPANY_WIDE to ensure data integrity
            if (visibility == DocumentVisibility.COMPANY_WIDE) {
                document.setAccessibleDepartments(null);
                document.setAccessibleRoles(null);
            } else if (visibility == DocumentVisibility.SPECIFIC_DEPARTMENTS) {
                document.setAccessibleDepartments(accessibleDepartments);
                document.setAccessibleRoles(null);
            } else if (visibility == DocumentVisibility.SPECIFIC_ROLES) {
                document.setAccessibleDepartments(null);
                document.setAccessibleRoles(accessibleRoles);
            }
            
            document.setOwnerDepartmentId(userDetails.getDepartmentId());
            document.setUploadedBy(userDetails.getId());
            document.setUploadedByName(userDetails.getEmail());
            document.setUploadedByEmail(userDetails.getEmail());
            document.setUploadedByRole(userDetails.getRoleCode());
            document.setUploadedAt(LocalDateTime.now());
            document.setEmbeddingStatus("PENDING");
            document.setEmbeddingModel("gemini-embedding-001");
            document.setIsActive(true);

            document = documentRepository.save(document);
            log.info("Document saved to database: {}", document.getId());

            // Trigger async processing
            documentProcessingService.processDocumentAsync(document.getId());
            log.info("Document processing triggered: {}", document.getId());

            // Build response
            return ResponseEntity.ok(toResponse(document));

        } catch (Exception e) {
            log.error("Failed to upload document", e);
            return ResponseEntity.internalServerError().body("Upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/detail/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_USER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Xem chi tiết tài liệu theo ID",
        description = """
            Trả về thông tin đầy đủ của tài liệu. Kiểm tra quyền truy cập theo `visibility`:
            - `COMPANY_WIDE`: tất cả user trong tenant đều xem được
            - `SPECIFIC_DEPARTMENTS`: chỉ user thuộc đúng department mới xem được
            - `SPECIFIC_ROLES`: chỉ user có role tương ứng mới xem được
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trả về DocumentResponse"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy hoặc không có quyền truy cập")
    })
    public ResponseEntity<?> getDocument(
            @Parameter(description = "ID của tài liệu", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        return documentRepository.findById(id)
                .filter(doc -> doc.getTenantId().equals(userDetails.getTenantId()))
                .filter(doc -> {
                    // Check access control
                    if (doc.getVisibility() == DocumentVisibility.COMPANY_WIDE) {
                        return true;
                    } else if (doc.getVisibility() == DocumentVisibility.SPECIFIC_DEPARTMENTS) {
                        return doc.getAccessibleDepartments() != null && 
                               doc.getAccessibleDepartments().contains(userDetails.getDepartmentId());
                    } else if (doc.getVisibility() == DocumentVisibility.SPECIFIC_ROLES) {
                        return doc.getAccessibleRoles() != null && 
                               doc.getAccessibleRoles().contains(userDetails.getRoleId());
                    }
                    return false;
                })
                .map(doc -> {
                    return ResponseEntity.ok(toResponse(doc));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_USER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Danh sách tài liệu của tenant",
        description = """
            Trả về tất cả tài liệu mà user hiện tại có quyền xem, bao gồm:
            - Tài liệu `COMPANY_WIDE`
            - Tài liệu `SPECIFIC_DEPARTMENTS` thuộc department của user
            - Tài liệu `SPECIFIC_ROLES` thuộc role của user

            Kết quả bao gồm cả tài liệu đang `PENDING` embedding.
            """
    )
    public ResponseEntity<List<DocumentResponse>> listDocuments(
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        // Use access control query - only return documents user can access
        List<DocumentEntity> documents = documentRepository.findAccessibleDocuments(
                userDetails.getTenantId(),
                userDetails.getId(),
                userDetails.getDepartmentId(),
                userDetails.getRoleId()
        );

        List<DocumentResponse> responses = documents.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Update document access control settings
     */
    @PutMapping("/update-access/{id}")
    @PreAuthorize("hasPermission('MANAGE_KNOWLEDGE_BASE')")
    @SecurityRequirement(name = "bearerAuth")
    @Transactional
    @Operation(
        summary = "Cập nhật quyền truy cập tài liệu",
        description = """
            Thay đổi `visibility` và danh sách được phép truy cập của tài liệu.
            Thay đổi được áp dụng cho cả tất cả `document_chunks` liên quan.

            **Request body:**
            ```json
            {
              "visibility": "SPECIFIC_DEPARTMENTS",
              "accessibleDepartments": [1, 2],
              "accessibleRoles": null
            }
            ```

            **Lưu ý:** Chỉ TENANT_ADMIN hoặc user có quyền `MANAGE_KNOWLEDGE_BASE` mới gọi được API này.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cập nhật thành công, trả về DocumentResponse mới"),
        @ApiResponse(responseCode = "400", description = "Visibility không hợp lệ hoặc thiếu thông tin bắt buộc"),
        @ApiResponse(responseCode = "403", description = "Không có quyền sửa tài liệu này"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy tài liệu")
    })
    public ResponseEntity<?> updateDocumentAccess(
            @Parameter(description = "ID của tài liệu cần cập nhật", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentAccessRequest request,
            @AuthenticationPrincipal UserPrincipal userDetails) {
        
        // Validate visibility and access settings
        if (request.visibility() == DocumentVisibility.COMPANY_WIDE) {
            if ((request.accessibleDepartments() != null && !request.accessibleDepartments().isEmpty()) ||
                (request.accessibleRoles() != null && !request.accessibleRoles().isEmpty())) {
                return ResponseEntity.badRequest().body(
                    "Khi chọn COMPANY_WIDE, không được set accessibleDepartments hoặc accessibleRoles"
                );
            }
        }
        
        if (request.visibility() == DocumentVisibility.SPECIFIC_DEPARTMENTS) {
            if (request.accessibleDepartments() == null || request.accessibleDepartments().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    "Khi chọn SPECIFIC_DEPARTMENTS, phải cung cấp danh sách accessibleDepartments"
                );
            }
        }
        
        if (request.visibility() == DocumentVisibility.SPECIFIC_ROLES) {
            if (request.accessibleRoles() == null || request.accessibleRoles().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    "Khi chọn SPECIFIC_ROLES, phải cung cấp danh sách accessibleRoles"
                );
            }
        }

        // Find document and verify ownership
        DocumentEntity document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài liệu"));

        if (!document.getTenantId().equals(userDetails.getTenantId())) {
            return ResponseEntity.status(403).body("Bạn không có quyền cập nhật tài liệu này");
        }

        // Update document access control
        document.setVisibility(request.visibility());
        
        // Force null for COMPANY_WIDE to ensure data integrity
        if (request.visibility() == DocumentVisibility.COMPANY_WIDE) {
            document.setAccessibleDepartments(null);
            document.setAccessibleRoles(null);
        } else if (request.visibility() == DocumentVisibility.SPECIFIC_DEPARTMENTS) {
            document.setAccessibleDepartments(request.accessibleDepartments());
            document.setAccessibleRoles(null);
        } else if (request.visibility() == DocumentVisibility.SPECIFIC_ROLES) {
            document.setAccessibleDepartments(null);
            document.setAccessibleRoles(request.accessibleRoles());
        }
        
        documentRepository.save(document);

        // Update all chunks associated with this document
        try {
            String accessibleDepartmentsJson;
            String accessibleRolesJson;
            
            if (request.visibility() == DocumentVisibility.COMPANY_WIDE) {
                // Force null for COMPANY_WIDE
                accessibleDepartmentsJson = null;
                accessibleRolesJson = null;
            } else {
                accessibleDepartmentsJson = (request.accessibleDepartments() != null) 
                    ? objectMapper.writeValueAsString(request.accessibleDepartments()) 
                    : null;
                accessibleRolesJson = (request.accessibleRoles() != null) 
                    ? objectMapper.writeValueAsString(request.accessibleRoles()) 
                    : null;
            }

            documentChunkRepository.updateChunkAccessControl(
                    id,
                    request.visibility().name(),
                    accessibleDepartmentsJson,
                    accessibleRolesJson
            );

            log.info("Updated access control for document {} and all its chunks. New visibility: {}", 
                    id, request.visibility());

        } catch (Exception e) {
            log.error("Error updating chunk access control for document {}: {}", id, e.getMessage());
            throw new RuntimeException("Lỗi khi cập nhật quyền truy cập cho các chunk: " + e.getMessage());
        }

        // Return updated document
        return ResponseEntity.ok(toResponse(document));
    }

    // =========================================================
    // POST /{id}/versions  — Upload phiên bản mới cho tài liệu
    // =========================================================

    /**
     * Upload file mới để tạo phên bản kế tiếp của tài liệu.
     *
     * Quy trình:
     *  1. Lưu snapshot của bản hiện tại vào document_versions.
     *  2. Upload file mới lên MinIO.
     *  3. Cập nhật bản ghi documents với file mới + tăng version_number.
     *  4. Kích hoạt process lại embedding.
     */
    @PostMapping(value = "/update/{id}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasPermission('MANAGE_KNOWLEDGE_BASE')")
    @SecurityRequirement(name = "bearerAuth")
    @Transactional
    @Operation(
        summary = "Upload phiên bản mới cho tài liệu",
        description = """
            Tạo phiên bản mới cho tài liệu đã tồn tại.

            **Quy trình:**
            1. Snapshot bản hiện tại vào bảng `document_versions`
            2. Upload file mới lên MinIO
            3. Cập nhật `version_number` (+1), thay thế file mới
            4. Xóa chunks cũ, kích hoạt re-embedding (async)

            **Lưu nhớ:** Sau khi upload, `embeddingStatus` chuyển về `PENDING` cho đến khi embedding hoàn tất.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Upload phiên bản mới thành công, trả về DocumentResponse với version_number mới"),
        @ApiResponse(responseCode = "400", description = "File rỗng / vượt 50MB / loại file không hỗ trợ"),
        @ApiResponse(responseCode = "403", description = "Tài liệu thuộc tenant khác"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy tài liệu")
    })
    public ResponseEntity<?> uploadNewVersion(
            @Parameter(description = "ID của tài liệu cần cập nhật phiên bản", required = true)
            @PathVariable UUID id,
            @Parameter(description = "File mới (cùng định dạng cho phép: PDF/DOCX/XLSX/PPTX/TXT/MD/CSV)", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Ghi chú về nội dung thay đổi trong phiên bản này")
            @RequestParam(value = "versionNote", required = false) String versionNote,
            @Parameter(description = "Tiêu đề mới cho tài liệu (nếu muốn đổi tỪn)")
            @RequestParam(value = "documentTitle", required = false) String documentTitle,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        try {
            // --- 1. Lấy tài liệu gốc ---
            DocumentEntity doc = documentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài liệu: " + id));

            if (!doc.getTenantId().equals(userDetails.getTenantId())) {
                return ResponseEntity.status(403).body("Bạn không có quyền cập nhật tài liệu này");
            }

            if (file.isEmpty()) return ResponseEntity.badRequest().body("File rỗng");
            if (file.getSize() > MAX_FILE_SIZE) return ResponseEntity.badRequest().body("File vượt quá 50MB");
            if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
                return ResponseEntity.badRequest().body("Loại file không được hỗ trợ");
            }

            // --- 2. Snapshot bản hiện tại vào document_versions ---
            DocumentVersion snapshot = DocumentVersion.builder()
                    .documentId(doc.getId())
                    .tenantId(doc.getTenantId())
                    .versionNumber(doc.getVersionNumber() != null ? doc.getVersionNumber() : 1)
                    .originalFileName(doc.getOriginalFileName())
                    .fileName(doc.getFileName())
                    .fileType(doc.getFileType())
                    .fileSize(doc.getFileSize())
                    .storagePath(doc.getStoragePath())
                    .versionNote(doc.getVersionNote())
                    .createdBy(userDetails.getId())
                    .createdByName(userDetails.getEmail())
                    .createdByEmail(userDetails.getEmail())
                    .createdAt(LocalDateTime.now())
                    .build();
            documentVersionRepository.save(snapshot);

            // --- 3. Upload file mới lên MinIO ---
            String folder = "tenant-" + userDetails.getTenantId() + "/documents";
            String newStoragePath = minioService.uploadDocument(file, folder);
            String newFileName = newStoragePath.substring(newStoragePath.lastIndexOf('/') + 1);

            // --- 4. Cập nhật bản ghi documents ---
            int newVersionNumber = (doc.getVersionNumber() != null ? doc.getVersionNumber() : 1) + 1;
            doc.setFileName(newFileName);
            doc.setOriginalFileName(file.getOriginalFilename());
            doc.setFileType(file.getContentType());
            doc.setFileSize(file.getSize());
            doc.setStoragePath(newStoragePath);
            doc.setVersionNumber(newVersionNumber);
            doc.setVersionNote(versionNote);
            if (documentTitle != null && !documentTitle.isBlank()) doc.setDocumentTitle(documentTitle);
            doc.setUpdatedBy(userDetails.getId());
            doc.setUpdatedAt(LocalDateTime.now());
            doc.setEmbeddingStatus("PENDING");
            doc.setEmbeddingError(null);
            doc.setChunkCount(null);

            documentRepository.save(doc);

            // --- 5. Xóa chunks cũ, re-embed ---
            documentChunkRepository.deleteByDocumentId(id);
            documentProcessingService.processDocumentAsync(id);

            log.info("New version {} uploaded for document {} by {}",
                    newVersionNumber, id, userDetails.getEmail());

            return ResponseEntity.ok(toResponse(doc));

        } catch (Exception e) {
            log.error("Failed to upload new version for document {}", id, e);
            return ResponseEntity.internalServerError().body("Upload phiên bản thất bại: " + e.getMessage());
        }
    }

    // =========================================================
    // GET /{id}/versions  — Lịch sử các phiên bản cũ
    // =========================================================

    @GetMapping("/versions/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_USER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Lịch sử phiên bản của tài liệu",
        description = """
            Trả về danh sách các phiên bản cũ đã được lưu trong `document_versions`.
            **Không bao gồm bản hiện tại** — để xem bản hiện tại dùng `GET /api/v1/knowledge/documents/{id}`.

            Kết quả sắp xếp giảm dần theo `version_number` (phiên bản mới nhất lên trước).

            Mỗi phần tử trả về: `versionId`, `versionNumber`, `originalFileName`, `fileType`, `fileSize`, `versionNote`, `createdByName`, `createdAt`.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Danh sách các phiên bản cũ"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy tài liệu hoặc tài liệu thuộc tenant khác")
    })
    public ResponseEntity<?> getVersionHistory(
            @Parameter(description = "ID của tài liệu", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        // Kiểm tra quyền tenant
        boolean belongs = documentRepository.findById(id)
                .map(d -> d.getTenantId().equals(userDetails.getTenantId()))
                .orElse(false);
        if (!belongs) return ResponseEntity.notFound().build();

        List<DocumentVersionResponse> history = documentVersionRepository
                .findByDocumentIdOrderByVersionNumberDesc(id)
                .stream()
                .map(v -> DocumentVersionResponse.builder()
                        .versionId(v.getId())
                        .documentId(v.getDocumentId())
                        .versionNumber(v.getVersionNumber())
                        .originalFileName(v.getOriginalFileName())
                        .fileType(v.getFileType())
                        .fileSize(v.getFileSize())
                        .versionNote(v.getVersionNote())
                        .createdByName(v.getCreatedByName())
                        .createdByEmail(v.getCreatedByEmail())
                        .createdAt(v.getCreatedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(history);
    }
}

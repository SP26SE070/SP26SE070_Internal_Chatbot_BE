package com.gsp26se114.chatbot_rag_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsp26se114.chatbot_rag_be.entity.DocumentEntity;
import com.gsp26se114.chatbot_rag_be.entity.DocumentVersion;
import com.gsp26se114.chatbot_rag_be.entity.DocumentVisibility;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateDocumentAccessRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.DocumentResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.DocumentVersionResponse;
import com.gsp26se114.chatbot_rag_be.repository.DocumentChunkRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentVersionRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.DocumentProcessingService;
import com.gsp26se114.chatbot_rag_be.service.MinioService;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "Upload document to Knowledge Base")
    public ResponseEntity<?> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "visibility", defaultValue = "COMPANY_WIDE") DocumentVisibility visibility,
            @RequestParam(value = "accessibleDepartments", required = false) List<Integer> accessibleDepartments,
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
            document.setCategory(category);
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

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_USER')")
    @Operation(summary = "Get document by ID (with access control)")
    public ResponseEntity<?> getDocument(
            @PathVariable UUID id,
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
    @Operation(summary = "List all documents for tenant (with access control)")
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
    @PutMapping("/{id}/access")
    @Operation(summary = "Update document access control")
    @PreAuthorize("hasPermission('MANAGE_KNOWLEDGE_BASE')")
    @Transactional
    public ResponseEntity<?> updateDocumentAccess(
            @PathVariable UUID id,
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
    @PostMapping(value = "/{id}/versions", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasPermission('MANAGE_KNOWLEDGE_BASE')")
    @Transactional
    @Operation(
        summary = "Upload phiên bản mới cho tài liệu",
        description = "Lưu bản hiện tại vào lịch sử, sau đó thay thế bằng file mới và re-embed."
    )
    public ResponseEntity<?> uploadNewVersion(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "versionNote", required = false) String versionNote,
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

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TENANT_USER')")
    @Operation(
        summary = "Lịch sử phiên bản của tài liệu",
        description = "Trả về danh sách các phiên bản cũ (không bao gồm bản hiện tại). Mới nhất trên đầu."
    )
    public ResponseEntity<?> getVersionHistory(
            @PathVariable UUID id,
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

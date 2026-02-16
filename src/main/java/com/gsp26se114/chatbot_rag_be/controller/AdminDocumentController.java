package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.service.DocumentProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin controller for document management operations.
 * Only accessible by SUPER_ADMIN users.
 */
@RestController
@RequestMapping("/api/v1/admin/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "99. 🔧 Admin - Document Management", description = "Quản lý tài liệu nâng cao (chỉ dành cho SUPER_ADMIN)")
public class AdminDocumentController {

    private final DocumentProcessingService documentProcessingService;

    @PostMapping("/{id}/reprocess")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Xử lý lại tài liệu (force reprocessing)", 
               description = "Xóa chunks cũ và xử lý lại tài liệu từ đầu. Chỉ SUPER_ADMIN có quyền.")
    public ResponseEntity<String> reprocessDocument(@PathVariable UUID id) {
        try {
            log.info("Admin reprocess document: {}", id);
            documentProcessingService.processDocumentSync(id);
            return ResponseEntity.ok("Tài liệu đã được xử lý lại thành công: " + id);
        } catch (Exception e) {
            log.error("Error reprocessing document {}", id, e);
            return ResponseEntity.internalServerError()
                    .body("Lỗi khi xử lý lại tài liệu: " + e.getMessage());
        }
    }
    
    @PostMapping("/{id}/reprocess-async")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Xử lý lại tài liệu (async)", 
               description = "Kích hoạt xử lý lại tài liệu ở chế độ async. Chỉ SUPER_ADMIN có quyền.")
    public ResponseEntity<String> reprocessDocumentAsync(@PathVariable UUID id) {
        try {
            log.info("Admin async reprocess document: {}", id);
            documentProcessingService.processDocumentAsync(id);
            return ResponseEntity.ok("Đã kích hoạt xử lý lại tài liệu (async): " + id);
        } catch (Exception e) {
            log.error("Error triggering async reprocess for document {}", id, e);
            return ResponseEntity.internalServerError()
                    .body("Lỗi khi kích hoạt xử lý lại: " + e.getMessage());
        }
    }
}

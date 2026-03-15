package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Snapshot lịch sử các phiên bản cũ của một tài liệu.
 *
 * Luồng tạo version mới:
 *  1. Lưu thông tin version_number + storage_path của bản tại thời điểm tạo version.
 *  2. Upload file mới lên MinIO.
 *  3. Cập nhật documents với file mới, tăng version_number, ghi version_note.
 *
 * Truy vấn lịch sử: SELECT * FROM document_versions WHERE document_id = :id ORDER BY version_number DESC
 */
@Entity
@Table(
    name = "document_versions",
    indexes = {
        @Index(name = "idx_doc_versions_document", columnList = "document_id"),
        @Index(name = "idx_doc_versions_tenant",   columnList = "tenant_id"),
        @Index(name = "idx_doc_versions_created",  columnList = "document_id, version_number")
    }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "version_id")
    private UUID id;

    /** FK về tài liệu gốc (stable ID không đổi qua các version) */
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // ========== VERSION SNAPSHOT ==========
    /** Số phiên bản (1, 2, 3...) của bản cũ được snapshot tại đây */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /** Path file cũ trên MinIO — vẫn giữ để cho phép xem/tải lại */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    // ========== CHANGE NOTE ==========
    /** Ghi chú về nội dung thay đổi, ví dụ: "Bổ sung quy định WFH Q2-2026" */
    @Column(name = "version_note", length = 500)
    private String versionNote;

    // ========== AUDIT ==========
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Snapshot lịch sử các phiên bản cũ của một tài liệu.
 *
 * Luồng tạo version mới:
 *  1. Copy metadata + storage_path của bản hiện tại vào document_versions.
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

    // ========== SNAPSHOT FILE INFO ==========
    /** Số phiên bản (1, 2, 3...) của bản cũ được snapshot tại đây */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 100)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

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

    /** Tên người tạo version này (denormalized để hiển thị nhanh trong UI) */
    @Column(name = "created_by_name", nullable = false, length = 200)
    private String createdByName;

    @Column(name = "created_by_email", nullable = false)
    private String createdByEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

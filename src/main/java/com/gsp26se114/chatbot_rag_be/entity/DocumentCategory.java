package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phân loại tài liệu dạng cây (nested), per-tenant.
 * Ví dụ: HR > Onboarding > New Hire Checklist
 */
@Entity
@Table(name = "document_categories", indexes = {
    @Index(name = "idx_doc_categories_tenant", columnList = "tenant_id"),
    @Index(name = "idx_doc_categories_parent", columnList = "parent_id")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "category_id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Parent category (null = root level) */
    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a document chunk with vector embedding for RAG system.
 * Uses pgvector extension for efficient similarity search.
 */
@Entity
@Table(name = "document_chunks", indexes = {
        @Index(name = "idx_chunk_document", columnList = "document_id"),
        @Index(name = "idx_chunk_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "document_chunk_id")
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Vector embedding stored as PostgreSQL vector type (pgvector extension).
     * gemini-embedding-001 with outputDimensionality=768 produces 768 dimensions
     */
    @Column(name = "embedding", columnDefinition = "vector(768)")
    private String embedding;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel; // "text-embedding-ada-002", "gemini-embedding-001", etc.

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Metadata copied from parent document for efficient filtering.
     * Avoids JOIN with documents table during similarity search.
     */
    @Column(name = "visibility", length = 30)
    private String visibility; // COMPANY_WIDE, SPECIFIC_DEPARTMENTS, SPECIFIC_ROLES

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accessible_departments", columnDefinition = "jsonb")
    private java.util.List<Integer> accessibleDepartments;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accessible_roles", columnDefinition = "jsonb")
    private java.util.List<Integer> accessibleRoles;

    @Column(name = "owner_department_id")
    private Integer ownerDepartmentId;

    /** Category của document cha (denormalized để filter nhanh) */
    @Column(name = "category_id")
    private UUID categoryId;

    /** Tag IDs của document cha (denormalized để filter nhanh trong RAG query) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tag_ids", columnDefinition = "jsonb")
    private List<UUID> tagIds;

    /** Từ khóa trích xuất từ chunk để pre-filter: ["onboarding", "HR", "checklist"] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keywords", columnDefinition = "jsonb")
    private java.util.List<String> keywords;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Check if this chunk is accessible by the given user based on visibility rules.
     */
    public boolean isAccessibleBy(Integer userDepartmentId, Integer userRoleId) {
        if (visibility == null || visibility.equals("COMPANY_WIDE")) {
            return true;
        }

        if (visibility.equals("SPECIFIC_DEPARTMENTS")) {
            return accessibleDepartments != null && accessibleDepartments.contains(userDepartmentId);
        }

        if (visibility.equals("SPECIFIC_ROLES")) {
            return accessibleRoles != null && accessibleRoles.contains(userRoleId);
        }

        return false;
    }
}

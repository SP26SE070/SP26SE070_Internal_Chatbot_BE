package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Repository for document chunks with vector similarity search using pgvector.
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    /**
     * Find top K most similar chunks to the query embedding with access control.
     * Uses pgvector's <-> operator for cosine distance.
     * Owner (uploaded_by) always has access regardless of visibility.
     *
     * @param tenantId          Tenant isolation
     * @param userId            User ID for owner check
     * @param queryEmbedding    Query vector in format '[0.1,0.2,0.3,...]'
     * @param userDepartmentId  User's department for access control
     * @param userRoleId        User's role for access control
     * @param maxDistance       Maximum cosine distance (0.35 = 65% similarity)
     * @param limit             Number of chunks to return
     * @return List of similar chunks ordered by relevance
     */
    @Query(value = """
        SELECT c.*
        FROM document_chunks c
        JOIN documents d ON c.document_id = d.document_id
        WHERE c.tenant_id = :tenantId
                    AND (:categoryId IS NULL OR c.category_id = :categoryId)
                    AND (:tagIds IS NULL OR c.tag_ids @> CAST(:tagIds AS jsonb))
          AND (c.embedding <=> CAST(:queryEmbedding AS vector)) < :maxDistance
          AND (
              d.uploaded_by = CAST(:userId AS uuid)
              OR c.visibility = 'COMPANY_WIDE'
              OR (
                  (d.active_version_id IS NULL OR c.version_id = d.active_version_id)
                  AND (
                      (c.visibility = 'SPECIFIC_DEPARTMENTS'
                       AND c.accessible_departments @> CAST(CONCAT('[', :userDepartmentId, ']') AS jsonb))
                      OR (c.visibility = 'SPECIFIC_ROLES'
                       AND c.accessible_roles @> CAST(CONCAT('[', :userRoleId, ']') AS jsonb))
                  )
              )
          )
        ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<DocumentChunkEntity> findSimilarChunksWithAccessControl(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("userDepartmentId") Integer userDepartmentId,
            @Param("userRoleId") Integer userRoleId,
                @Param("categoryId") UUID categoryId,
                @Param("tagIds") String tagIds,
            @Param("maxDistance") double maxDistance,
            @Param("limit") int limit
    );

    /**
     * Find all chunks for a specific document (for re-indexing or deletion).
     */
    List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndex(UUID documentId);

    /**
     * Delete all chunks for a document (when document is deleted).
     */
    void deleteByDocumentId(UUID documentId);

    /**
     * Count total chunks for a document.
     */
    long countByDocumentId(UUID documentId);

    /**
     * Update access control for all chunks of a document
     */
    @Modifying
    @Query(value = """
        UPDATE document_chunks
        SET visibility = :visibility,
            accessible_departments = CAST(:accessibleDepartments AS jsonb),
            accessible_roles = CAST(:accessibleRoles AS jsonb)
        WHERE document_id = :documentId
        """, nativeQuery = true)
    void updateChunkAccessControl(
            @Param("documentId") UUID documentId,
            @Param("visibility") String visibility,
            @Param("accessibleDepartments") String accessibleDepartments,
            @Param("accessibleRoles") String accessibleRoles
    );

    /**
     * Insert chunk with explicit vector casting for PostgreSQL
     * Required because JPA doesn't handle String to vector type conversion
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO document_chunks (
            document_chunk_id, document_id, tenant_id, chunk_index, content,
            embedding, embedding_model, token_count,
            visibility, accessible_departments, accessible_roles, owner_department_id,
            category_id, tag_ids, version_id,
            created_at
        ) VALUES (
            :id, :documentId, :tenantId, :chunkIndex, :content,
            CAST(:embedding AS vector(768)), :embeddingModel, :tokenCount,
            :visibility, CAST(:accessibleDepartments AS jsonb), CAST(:accessibleRoles AS jsonb), :ownerDepartmentId,
            :categoryId, CAST(:tagIds AS jsonb), :versionId,
            :createdAt
        )
        """, nativeQuery = true)
    void insertChunkWithVectorCast(
            @Param("id") UUID id,
            @Param("documentId") UUID documentId,
            @Param("tenantId") UUID tenantId,
            @Param("chunkIndex") Integer chunkIndex,
            @Param("content") String content,
            @Param("embedding") String embedding,
            @Param("embeddingModel") String embeddingModel,
            @Param("tokenCount") Integer tokenCount,
            @Param("visibility") String visibility,
            @Param("accessibleDepartments") String accessibleDepartments,
            @Param("accessibleRoles") String accessibleRoles,
                @Param("categoryId") UUID categoryId,
                @Param("tagIds") String tagIds,
            @Param("versionId") UUID versionId,
            @Param("ownerDepartmentId") Integer ownerDepartmentId,
            @Param("createdAt") java.time.LocalDateTime createdAt
    );

    /**
     * Count total chunks by tenant ID (through document relationship)
     */
    @Query(value = "SELECT COUNT(*) FROM document_chunks c " +
                   "INNER JOIN documents d ON c.document_id = d.document_id " +
                   "WHERE d.tenant_id = :tenantId",
           nativeQuery = true)
    long countByTenantId(@Param("tenantId") UUID tenantId);
}

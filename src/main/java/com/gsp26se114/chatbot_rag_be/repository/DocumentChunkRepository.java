package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * 
     * @param tenantId          Tenant isolation
     * @param queryEmbedding    Query vector in format '[0.1,0.2,0.3,...]'
     * @param userDepartmentId  User's department for access control
     * @param userRoleId        User's role for access control
     * @param limit             Number of chunks to return
     * @return List of similar chunks ordered by relevance
     */
    @Query(value = """
        SELECT *
        FROM document_chunks
        WHERE tenant_id = :tenantId
          AND (
              visibility = 'COMPANY_WIDE'
              OR (visibility = 'SPECIFIC_DEPARTMENTS' 
                  AND jsonb_exists(accessible_departments, CAST(:userDepartmentId AS text)))
              OR (visibility = 'SPECIFIC_ROLES' 
                  AND jsonb_exists(accessible_roles, CAST(:userRoleId AS text)))
          )
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<DocumentChunkEntity> findSimilarChunksWithAccessControl(
            @Param("tenantId") UUID tenantId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("userDepartmentId") Integer userDepartmentId,
            @Param("userRoleId") Integer userRoleId,
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
     * Insert chunk with explicit vector casting for PostgreSQL
     * Required because JPA doesn't handle String to vector type conversion
     */
    @Modifying
    @Query(value = """
        INSERT INTO document_chunks (
            id, document_id, tenant_id, chunk_index, content, 
            embedding, embedding_model, token_count, 
            visibility, accessible_departments, accessible_roles, owner_department_id,
            created_at
        ) VALUES (
            :id, :documentId, :tenantId, :chunkIndex, :content,
            CAST(:embedding AS vector), :embeddingModel, :tokenCount,
            :visibility, CAST(:accessibleDepartments AS jsonb), CAST(:accessibleRoles AS jsonb), :ownerDepartmentId,
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
            @Param("ownerDepartmentId") Integer ownerDepartmentId,
            @Param("createdAt") java.time.LocalDateTime createdAt
    );
    
    /**
     * Count total chunks by tenant ID (through document relationship)
     */
    @Query(value = "SELECT COUNT(*) FROM document_chunks c " +
                   "INNER JOIN documents d ON c.document_id = d.id " +
                   "WHERE d.tenant_id = :tenantId", 
           nativeQuery = true)
    long countByTenantId(@Param("tenantId") UUID tenantId);
}

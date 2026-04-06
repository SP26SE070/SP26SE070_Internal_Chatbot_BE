package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.DocumentEntity;
import com.gsp26se114.chatbot_rag_be.entity.DocumentVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    
    /**
     * Lấy tất cả documents của tenant (cho Document Dashboard)
     */
    List<DocumentEntity> findByTenantIdAndIsActiveOrderByUploadedAtDesc(UUID tenantId, Boolean isActive);
    
    /**
     * Lấy documents mà user có quyền xem (cho RAG query)
     * Logic:
     * 1. COMPANY_WIDE → Tất cả xem được
     * 2. SPECIFIC_DEPARTMENTS → Check userDepartmentId trong JSONB array
     * 3. SPECIFIC_ROLES → Check userRoleId trong JSONB array
     * 
     * Dùng native query vì Hibernate 6 không support MEMBER OF với JSONB
     */
    @Query(value = """
        SELECT * FROM documents 
        WHERE tenant_id = :tenantId 
        AND is_active = true
        AND (
            uploaded_by = CAST(:userId AS uuid)
            OR visibility = 'COMPANY_WIDE'
            OR (visibility = 'SPECIFIC_DEPARTMENTS'
                AND accessible_departments @> CAST(CONCAT('[', :userDepartmentId, ']') AS jsonb))
            OR (visibility = 'SPECIFIC_ROLES'
                AND accessible_roles @> CAST(CONCAT('[', :userRoleId, ']') AS jsonb))
            OR (visibility = 'SPECIFIC_DEPARTMENTS_AND_ROLES'
                AND accessible_departments @> CAST(CONCAT('[', :userDepartmentId, ']') AS jsonb)
                AND accessible_roles @> CAST(CONCAT('[', :userRoleId, ']') AS jsonb))
        )
        ORDER BY uploaded_at DESC
        """, nativeQuery = true)
    List<DocumentEntity> findAccessibleDocuments(
        @Param("tenantId") UUID tenantId,
        @Param("userId") UUID userId,
        @Param("userDepartmentId") Integer userDepartmentId,
        @Param("userRoleId") Integer userRoleId
    );
    
    /**
     * Count documents theo visibility
     */
    Long countByTenantIdAndVisibilityAndIsActive(UUID tenantId, DocumentVisibility visibility, Boolean isActive);
    
    /**
     * Lấy documents được upload bởi user
     */
    List<DocumentEntity> findByUploadedByAndIsActiveOrderByUploadedAtDesc(UUID uploadedBy, Boolean isActive);

    /**
     * Lấy danh sách documents đã xóa mềm của tenant
     */
    List<DocumentEntity> findByTenantIdAndIsActiveOrderByDeletedAtDesc(UUID tenantId, Boolean isActive);
    
    /**
     * Count documents của user (để check quota)
     */
    Long countByTenantIdAndIsActive(UUID tenantId, Boolean isActive);
    
    /**
     * Lấy document by ID và check tenant isolation
     */
    Optional<DocumentEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    
    /**
     * Lấy documents có embedding status = PENDING (để process)
     */
    List<DocumentEntity> findByEmbeddingStatusAndIsActive(String embeddingStatus, Boolean isActive);
    
    /**
     * Count total documents by tenant ID
     */
    long countByTenantId(UUID tenantId);

    /**
     * Count documents by tenant, embeddingStatus and isActive (cho dashboard breakdown)
     */
    Long countByTenantIdAndEmbeddingStatusAndIsActive(UUID tenantId, String embeddingStatus, Boolean isActive);
}

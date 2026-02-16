package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Document entity lưu thông tin tài liệu được upload vào Knowledge Base
 * Support access control theo department hoặc role
 */
@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_tenant_visibility", columnList = "tenant_id, visibility"),
    @Index(name = "idx_uploaded_at", columnList = "uploaded_at")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    // ========== BASIC INFO ==========
    @Column(nullable = false)
    private String fileName;           // "uuid_Employee_Handbook.pdf"
    
    @Column(nullable = false)
    private String originalFileName;   // "Employee Handbook.pdf" (tên gốc từ user)
    
    @Column(nullable = false)
    private String fileType;           // "application/pdf", "text/plain"
    
    @Column(nullable = false)
    private Long fileSize;             // Bytes
    
    @Column(nullable = false, length = 500)
    private String storagePath;        // "tenant-123/documents/uuid_file.pdf" (path trong MinIO/local)
    
    // ========== TENANT & CATEGORY ==========
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;             // Thuộc tenant nào
    
    @Column(length = 100)
    private String category;           // "HR", "IT", "Finance", "Onboarding"
    
    @Column(length = 1000)
    private String description;        // Mô tả tài liệu
    
    // ========== ACCESS CONTROL ==========
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 30)
    private DocumentVisibility visibility = DocumentVisibility.COMPANY_WIDE;
    
    @Column(name = "owner_department_id")
    private Integer ownerDepartmentId; // Department của người upload
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accessible_departments", columnDefinition = "jsonb")
    private List<Integer> accessibleDepartments; // [1, 2, 5] nếu visibility = SPECIFIC_DEPARTMENTS
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accessible_roles", columnDefinition = "jsonb")
    private List<Integer> accessibleRoles;       // [3, 6, 8] nếu visibility = SPECIFIC_ROLES
    
    // ========== UPLOAD HISTORY (AUDIT TRAIL) ==========
    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;           // User ID người upload
    
    @Column(name = "uploaded_by_name", nullable = false, length = 200)
    private String uploadedByName;     // "Nguyễn Văn A" (denormalized)
    
    @Column(name = "uploaded_by_email", nullable = false)
    private String uploadedByEmail;    // "admin@fpt.com" (denormalized)
    
    @Column(name = "uploaded_by_role", length = 100)
    private String uploadedByRole;     // "TENANT_ADMIN" hoặc "HR_MANAGER" (denormalized)
    
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();
    
    // ========== UPDATE HISTORY ==========
    @Column(name = "updated_by")
    private UUID updatedBy;            // User ID người update cuối
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;   // Thời gian update cuối
    
    @Column(nullable = false)
    private Integer version = 1;       // Version của document
    
    // ========== VECTOR DB INFO ==========
    @Column(name = "vector_db_id", length = 200)
    private String vectorDbId;         // ID trong Vector DB (Pinecone, Weaviate, etc.)
    
    @Column(name = "embedding_status", length = 20)
    private String embeddingStatus = "PENDING"; // "PENDING", "PROCESSING", "COMPLETED", "FAILED"
    
    @Column(name = "chunk_count")
    private Integer chunkCount;        // Số chunks đã tạo
    
    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;     // "text-embedding-ada-002", "text-embedding-004"
    
    @Column(name = "embedding_error", length = 1000)
    private String embeddingError;     // Error message nếu embedding failed
    
    // ========== STATUS ==========
    @Column(nullable = false)
    private Boolean isActive = true;   // Soft delete
    
    @Column(name = "deleted_by")
    private UUID deletedBy;            // User ID người xóa (soft delete)
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;   // Thời gian xóa
    
    // ========== USAGE STATS ==========
    @Column(name = "view_count")
    private Long viewCount = 0L;       // Số lần được query qua chatbot
    
    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt; // Lần truy vấn cuối qua RAG
    
    // ========== HELPER METHODS ==========
    
    /**
     * Check nếu user có quyền xem document này
     */
    public boolean isAccessibleBy(Integer userDepartmentId, Integer userRoleId) {
        if (!isActive) {
            return false;
        }
        
        switch (visibility) {
            case COMPANY_WIDE:
                return true;
                
            case SPECIFIC_DEPARTMENTS:
                return accessibleDepartments != null && 
                       accessibleDepartments.contains(userDepartmentId);
                       
            case SPECIFIC_ROLES:
                return accessibleRoles != null && 
                       accessibleRoles.contains(userRoleId);
                       
            default:
                return false;
        }
    }
    
    /**
     * Increment view count khi được query qua chatbot
     */
    public void incrementViewCount() {
        this.viewCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }
    
    /**
     * Soft delete document
     */
    public void softDelete(UUID deletedBy) {
        this.isActive = false;
        this.deletedBy = deletedBy;
        this.deletedAt = LocalDateTime.now();
    }
}

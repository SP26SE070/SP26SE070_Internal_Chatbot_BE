package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, UUID> {

    /** Lấy tất cả category đang active của tenant */
    List<DocumentCategory> findByTenantIdAndIsActiveTrueOrderByNameAsc(UUID tenantId);

    /** Lấy các category cấp root (parent_id IS NULL) của tenant */
    List<DocumentCategory> findByTenantIdAndParentIdIsNullAndIsActiveTrueOrderByNameAsc(UUID tenantId);

    /** Lấy các sub-category của một parent */
    List<DocumentCategory> findByTenantIdAndParentIdAndIsActiveTrueOrderByNameAsc(UUID tenantId, UUID parentId);

    /** Tìm theo code trong tenant (code phải unique trong tenant) */
    Optional<DocumentCategory> findByTenantIdAndCode(UUID tenantId, String code);

    /** Kiểm tra code đã tồn tại trong tenant chưa (dùng khi tạo mới) */
    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    /** Kiểm tra code đã tồn tại trong tenant chưa (dùng khi update, loại trừ chính nó) */
    boolean existsByTenantIdAndCodeAndIdNot(UUID tenantId, String code, UUID excludeId);

    /** Kiểm tra có sub-category nào đang active không (dùng trước khi xóa) */
    @Query("SELECT COUNT(c) > 0 FROM DocumentCategory c WHERE c.parentId = :id AND c.isActive = true")
    boolean hasActiveChildren(@Param("id") UUID id);
}

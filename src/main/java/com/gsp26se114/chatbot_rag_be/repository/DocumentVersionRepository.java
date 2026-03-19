package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    /** Lấy toàn bộ lịch sử version của một tài liệu, sắp xếp mới nhất trên đầu */
    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    /** Tìm version theo id + document + tenant để validate ownership */
    java.util.Optional<DocumentVersion> findByIdAndDocumentIdAndTenantId(UUID id, UUID documentId, UUID tenantId);

    /** Đếm số version đã lưu cho một tài liệu */
    long countByDocumentId(UUID documentId);
}

package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.DocumentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentTagRepository extends JpaRepository<DocumentTag, UUID> {

    List<DocumentTag> findByTenantIdAndIsActiveTrueOrderByNameAsc(UUID tenantId);

    List<DocumentTag> findByTenantIdOrderByNameAsc(UUID tenantId);

    List<DocumentTag> findByTenantIdAndIdInAndIsActiveTrue(UUID tenantId, Collection<UUID> ids);

    Optional<DocumentTag> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(UUID tenantId, String code, UUID excludeId);
}
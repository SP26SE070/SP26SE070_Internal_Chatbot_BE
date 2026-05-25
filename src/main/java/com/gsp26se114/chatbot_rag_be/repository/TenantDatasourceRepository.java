package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.TenantDatasource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantDatasourceRepository extends JpaRepository<TenantDatasource, UUID> {
    Optional<TenantDatasource> findByTenantIdAndIsActiveTrue(UUID tenantId);
    List<TenantDatasource> findAllByIsActiveTrue();
    boolean existsByTenantId(UUID tenantId);
}

package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.entity.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    
    /**
     * Find all tenants by status
     * @param status The tenant status (PENDING, ACTIVE, REJECTED, SUSPENDED)
     * @return List of tenants with the specified status
     */
    List<Tenant> findByStatus(TenantStatus status);
    
    /**
     * Count tenants by status
     * @param status The tenant status
     * @return Number of tenants with the specified status
     */
    long countByStatus(TenantStatus status);
    
    /**
     * Check if a tenant with the given name exists
     * @param name Tenant name
     * @return true if exists
     */
    boolean existsByName(String name);
    
    /**
     * Check if a tenant with the given contact email exists
     * @param contactEmail Contact email
     * @return true if exists
     */
    boolean existsByContactEmail(String contactEmail);
}

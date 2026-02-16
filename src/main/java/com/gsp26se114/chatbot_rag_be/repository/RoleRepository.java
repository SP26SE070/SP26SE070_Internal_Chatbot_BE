package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Integer> {
    
    // ========== EXISTING METHODS (Keep for backward compatibility) ==========
    Optional<RoleEntity> findByCode(String code);
    List<RoleEntity> findByTenantIdIsNull(); // System roles
    List<RoleEntity> findByTenantId(UUID tenantId); // Tenant-specific roles
    boolean existsByCodeAndTenantId(String code, UUID tenantId);
    
    // ========== NEW METHODS FOR ROLE TYPE ==========
    
    /**
     * Find roles by role type
     */
    List<RoleEntity> findByRoleType(RoleType roleType);
    
    /**
     * Find active roles by role type
     */
    List<RoleEntity> findByRoleTypeAndIsActive(RoleType roleType, Boolean isActive);
    
    /**
     * Find tenant fixed roles (available to all tenants)
     */
    @Query("SELECT r FROM RoleEntity r WHERE r.roleType = 'FIXED' AND r.isActive = true")
    List<RoleEntity> findTenantFixedRoles();
    
    /**
     * Find custom roles for specific tenant
     */
    List<RoleEntity> findByTenantIdAndRoleType(UUID tenantId, RoleType roleType);
    
    /**
     * Find all roles available to a tenant (fixed + custom)
     */
    @Query("SELECT r FROM RoleEntity r WHERE " +
           "(r.roleType = 'FIXED' OR r.tenantId = :tenantId) " +
           "AND r.isActive = true " +
           "ORDER BY r.roleType, r.code")
    List<RoleEntity> findAvailableRolesForTenant(@Param("tenantId") UUID tenantId);
    
    /**
     * Find role by code and tenant (for custom roles)
     */
    Optional<RoleEntity> findByCodeAndTenantId(String code, UUID tenantId);
    
    /**
     * Count users assigned to role (for deletion check)
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.roleId = :roleId")
    long countUsersWithRole(@Param("roleId") Integer roleId);
    
    /**
     * Check if custom role code already exists in tenant
     */
    boolean existsByCodeAndTenantIdAndRoleType(String code, UUID tenantId, RoleType roleType);
    
    /**
     * Find system roles only
     */
    @Query("SELECT r FROM RoleEntity r WHERE r.roleType = 'SYSTEM' AND r.isActive = true")
    List<RoleEntity> findSystemRoles();
}


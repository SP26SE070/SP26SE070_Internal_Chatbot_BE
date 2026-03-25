package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.RoleType;
import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateRoleRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateRoleRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.RoleResponse;
import com.gsp26se114.chatbot_rag_be.repository.RoleRepository;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantRoleService {
    
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    
    /**
     * Get all roles available for a tenant (FIXED + CUSTOM)
     */
    public List<RoleResponse> getAvailableRolesForTenant(UUID tenantId) {
        log.info("Fetching available roles for tenant: {}", tenantId);
        List<RoleEntity> roles = roleRepository.findAvailableRolesForTenant(tenantId);
        return roles.stream()
                .filter(role -> {
                    String code = role.getCode();
                    return code != null && !code.equals("TENANT_ADMIN")
                            && !code.equals("SUPER_ADMIN") && !code.equals("STAFF");
                })
                .map(role -> mapToResponse(role, tenantId))
                .collect(Collectors.toList());
    }
    
    /**
     * Get only custom roles created by tenant
     */
    public List<RoleResponse> getCustomRolesForTenant(UUID tenantId) {
        log.info("Fetching custom roles for tenant: {}", tenantId);
        List<RoleEntity> roles = roleRepository.findByTenantIdAndRoleType(tenantId, RoleType.CUSTOM);
        return roles.stream()
                .map(role -> mapToResponse(role, tenantId))
                .collect(Collectors.toList());
    }
    
    /**
     * Get only fixed roles (available to all tenants), with per-tenant {@code usersCount}.
     */
    public List<RoleResponse> getTenantFixedRoles(UUID tenantId) {
        log.info("Fetching tenant fixed roles for tenant {}", tenantId);
        List<RoleEntity> roles = roleRepository.findTenantFixedRoles();
        return roles.stream()
                .map(role -> mapToResponse(role, tenantId))
                .collect(Collectors.toList());
    }
    
    /**
     * Get role by ID (check tenant ownership for custom roles)
     */
    public RoleResponse getRoleById(Integer roleId, UUID tenantId) {
        log.info("Fetching role by ID: {} for tenant: {}", roleId, tenantId);
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NoSuchElementException(
                    "Role không tồn tại với ID: " + roleId));

        // Block SYSTEM roles — Tenant Admin cannot view SUPER_ADMIN or STAFF
        if (role.isSystemRole()) {
            throw new AccessDeniedException(
                "Bạn không có quyền xem role hệ thống này."
            );
        }

        // Block custom roles from other tenants
        if (role.isCustomRole() && !role.belongsToTenant(tenantId)) {
            throw new AccessDeniedException(
                "Role này thuộc về tenant khác."
            );
        }

        return mapToResponse(role, tenantId);
    }
    
    /**
     * Create custom role (only TENANT_ADMIN can create)
     */
    @Transactional
    public RoleResponse createCustomRole(UUID tenantId, CreateRoleRequest request, UUID createdBy) {
        log.info("Creating custom role: {} for tenant: {}", request.code(), tenantId);
        
        // Validate tenant exists
        if (!tenantRepository.existsById(tenantId)) {
            throw new RuntimeException("Tenant không tồn tại");
        }
        
        // Validate code uniqueness within tenant
        if (roleRepository.existsByCodeAndTenantIdAndRoleType(request.code(), tenantId, RoleType.CUSTOM)) {
            throw new RuntimeException("Role code đã tồn tại trong tenant này: " + request.code());
        }
        
        // Validate permissions
        permissionService.validatePermissions(request.permissions());
        
        // Create custom role
        RoleEntity role = new RoleEntity();
        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setTenantId(tenantId);
        role.setRoleType(RoleType.CUSTOM);
        role.setPermissions(request.permissions());
        role.setCreatedBy(createdBy);
        role.setCreatedAt(LocalDateTime.now());
        role.setIsActive(true);
        
        RoleEntity savedRole = roleRepository.save(role);
        log.info("Created custom role: {} (ID: {}) for tenant: {}", savedRole.getCode(), savedRole.getId(), tenantId);
        
        return mapToResponse(savedRole, tenantId);
    }
    
    /**
     * Update custom role (only modify name, description, permissions - not code)
     */
    @Transactional
    public RoleResponse updateCustomRole(Integer roleId, UUID tenantId, UpdateRoleRequest request) {
        log.info("Updating custom role ID: {} for tenant: {}", roleId, tenantId);
        
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role không tồn tại với ID: " + roleId));
        
        // Security checks
        if (!role.isCustomRole()) {
            throw new RuntimeException("Không thể sửa system hoặc fixed roles");
        }
        if (!role.belongsToTenant(tenantId)) {
            throw new RuntimeException("Role này thuộc về tenant khác");
        }
        
        // Validate permissions
        permissionService.validatePermissions(request.permissions());
        
        // Update fields
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(request.permissions());
        role.setUpdatedAt(LocalDateTime.now());
        
        RoleEntity updatedRole = roleRepository.save(role);
        log.info("Updated custom role: {}", updatedRole.getCode());
        
        return mapToResponse(updatedRole, tenantId);
    }
    
    /**
     * Delete custom role (check users assigned)
     */
    @Transactional
    public void deleteCustomRole(Integer roleId, UUID tenantId) {
        log.info("Deleting custom role ID: {} for tenant: {}", roleId, tenantId);
        
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role không tồn tại với ID: " + roleId));
        
        // Security checks
        if (!role.isCustomRole()) {
            throw new RuntimeException("Không thể xóa system hoặc fixed roles");
        }
        if (!role.belongsToTenant(tenantId)) {
            throw new RuntimeException("Role này thuộc về tenant khác");
        }
        
        long usersCount = userRepository.countByTenantIdAndRoleId(tenantId, roleId);
        if (usersCount > 0) {
            throw new RuntimeException("Không thể xóa role - " + usersCount + " users đang được gán role này");
        }
        
        roleRepository.delete(role);
        log.info("Deleted custom role: {}", role.getCode());
    }
    
    /**
     * Map RoleEntity to RoleResponse
     */
    private RoleResponse mapToResponse(RoleEntity role, UUID requestingTenantId) {
        String tenantName = null;
        if (role.getTenantId() != null) {
            tenantName = tenantRepository.findById(role.getTenantId())
                    .map(Tenant::getName)
                    .orElse(null);
        }
        
        long usersCount = resolveUsersCountForTenant(role, requestingTenantId);

        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .tenantId(role.getTenantId())
                .tenantName(tenantName)
                .roleType(role.getRoleType().name())
                .isSystemRole(role.isSystemRole())
                .isFixed(role.isTenantFixedRole())
                .isCustom(role.isCustomRole())
                .permissions(role.getPermissions())
                .usersCount(usersCount) // always set; see resolveUsersCountForTenant
                .createdBy(role.getCreatedBy())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    /**
     * Users assigned to this role within the tenant (same {@code user.roleId} as {@code role.id}).
     * Includes ACTIVE and INACTIVE users; “soft delete” in this app is {@code isActive=false}, still counted until role changes.
     * No separate soft-delete column on User.
     */
    private long resolveUsersCountForTenant(RoleEntity role, UUID requestingTenantId) {
        if (requestingTenantId == null || role.isSystemRole()) {
            return 0L;
        }
        if (role.isTenantFixedRole()) {
            return userRepository.countByTenantIdAndRoleId(requestingTenantId, role.getId());
        }
        if (role.isCustomRole() && role.belongsToTenant(requestingTenantId)) {
            return userRepository.countByTenantIdAndRoleId(requestingTenantId, role.getId());
        }
        return 0L;
    }
}

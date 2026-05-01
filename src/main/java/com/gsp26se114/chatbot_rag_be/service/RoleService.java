package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {
    
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    
    /**
     * Get all roles (system + tenant-specific)
     */
    public List<RoleResponse> getAllRoles() {
        log.info("Fetching all roles");
        List<RoleEntity> roles = roleRepository.findAll();
        return roles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all system roles (tenantId = null)
     */
    public List<RoleResponse> getSystemRoles() {
        log.info("Fetching system roles");
        List<RoleEntity> roles = roleRepository.findByTenantIdIsNull();
        return roles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get roles by tenant
     */
    public List<RoleResponse> getRolesByTenant(UUID tenantId) {
        log.info("Fetching roles for tenant: {}", tenantId);
        List<RoleEntity> roles = roleRepository.findByTenantId(tenantId);
        return roles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get role by ID
     */
    public RoleResponse getRoleById(Integer roleId) {
        log.info("Fetching role by ID: {}", roleId);
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role không tồn tại với ID: " + roleId));
        return mapToResponse(role);
    }
    
    /**
     * Create new role (DEPRECATED - use TenantRoleService for custom roles)
     * This method is kept for backward compatibility with AdminRoleController
     */
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        log.info("Creating new role: {}", request.code());
        
        // Validate code uniqueness
        if (roleRepository.findByCode(request.code()).isPresent()) {
            throw new RuntimeException("Role code đã tồn tại: " + request.code());
        }
        
        // Note: This old method doesn't support tenantId anymore
        // For custom tenant roles, use TenantRoleService
        
        RoleEntity role = new RoleEntity();
        role.setCode(request.code());
        role.setName(request.name());
        role.setLevel(request.level());
        role.setDescription(request.description());
        role.setTenantId(null); // System role by default
        role.setPermissions(request.permissions());
        role.setCreatedAt(LocalDateTime.now());
        
        RoleEntity savedRole = roleRepository.save(role);
        log.info("Created role: {} (ID: {})", savedRole.getCode(), savedRole.getId());
        
        return mapToResponse(savedRole);
    }
    
    /**
     * Update role (DEPRECATED for custom roles - use TenantRoleService)
     */
    @Transactional
    public RoleResponse updateRole(Integer roleId, UpdateRoleRequest request) {
        log.info("Updating role ID: {}", roleId);
        
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role không tồn tại với ID: " + roleId));
        
        // Update fields
        if (request.name() != null && !request.name().trim().isEmpty()) {
            role.setName(request.name());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        if (request.level() != null) {
            role.setLevel(request.level());
        }
        if (request.permissions() != null) {
            role.setPermissions(request.permissions());
        }
        role.setUpdatedAt(LocalDateTime.now());
        
        RoleEntity updatedRole = roleRepository.save(role);
        log.info("Updated role: {}", updatedRole.getCode());
        
        return mapToResponse(updatedRole);
    }
    
    /**
     * Delete role
     */
    @Transactional
    public void deleteRole(Integer roleId) {
        log.info("Deleting role ID: {}", roleId);
        
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role không tồn tại với ID: " + roleId));
        
        // Check if any users have this role
        long usersWithRole = userRepository.countByRoleId(roleId);
        if (usersWithRole > 0) {
            throw new RuntimeException("Không thể xóa role vì có " + usersWithRole + " users đang sử dụng!");
        }
        
        // Prevent deleting system roles (ID 1-4)
        if (roleId >= 1 && roleId <= 4) {
            throw new RuntimeException("Không thể xóa system roles mặc định!");
        }
        
        roleRepository.delete(role);
        log.info("Deleted role: {}", role.getCode());
    }
    
    /**
     * Map RoleEntity to RoleResponse
     */
    private RoleResponse mapToResponse(RoleEntity role) {
        String tenantName = null;
        if (role.getTenantId() != null) {
            tenantName = tenantRepository.findById(role.getTenantId())
                    .map(Tenant::getName)
                    .orElse(null);
        }
        
        Long usersCount = roleRepository.countUsersWithRole(role.getId());
        
        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .level(role.getLevel())
                .description(role.getDescription())
                .tenantId(role.getTenantId())
                .tenantName(tenantName)
                .roleType(role.getRoleType().name())
                .isSystemRole(role.isSystemRole())
                .isFixed(role.isTenantFixedRole())
                .isCustom(role.isCustomRole())
                .permissions(role.getPermissions())
                .usersCount(usersCount)
                .createdBy(role.getCreatedBy())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}

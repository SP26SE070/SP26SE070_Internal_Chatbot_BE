package com.gsp26se114.chatbot_rag_be.service;


import com.gsp26se114.chatbot_rag_be.constants.RolePermissionConstants;
import com.gsp26se114.chatbot_rag_be.entity.Department;
import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.entity.AuditLog;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateUserRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateUserRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.TenantAnalyticsResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.UserResponse;
import com.gsp26se114.chatbot_rag_be.repository.AuditLogRepository;
import com.gsp26se114.chatbot_rag_be.repository.DepartmentRepository;
import com.gsp26se114.chatbot_rag_be.repository.RoleRepository;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import com.gsp26se114.chatbot_rag_be.util.UserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAdminService {
    
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    
    /**
     * Get tenant dashboard analytics
     */
    public TenantAnalyticsResponse getTenantAnalytics(String tenantAdminEmail) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        UUID tenantId = tenantAdmin.getTenantId();
        
        log.info("Fetching analytics for tenant: {}", tenantId);
        
        // Get all users in tenant
        List<User> allUsers = userRepository.findByTenantId(tenantId);
        
        // Calculate time boundaries
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime last7Days = now.minusDays(7);
        LocalDateTime last30Days = now.minusDays(30);
        
        // ========== USER STATISTICS ==========
        int totalUsers = allUsers.size();
        
        // Active users (logged in within last 30 days)
        long activeUsers = allUsers.stream()
                .filter(u -> u.getLastLoginAt() != null && u.getLastLoginAt().isAfter(last30Days))
                .count();
        
        // New users this month
        long newUsersThisMonth = allUsers.stream()
                .filter(u -> u.getCreatedAt().isAfter(startOfMonth))
                .count();
        
        // Users created in last 7 days
        long usersCreatedLast7Days = allUsers.stream()
                .filter(u -> u.getCreatedAt().isAfter(last7Days))
                .count();
        
        // Users created in last 30 days
        long usersCreatedLast30Days = allUsers.stream()
                .filter(u -> u.getCreatedAt().isAfter(last30Days))
                .count();
        
        // ========== USERS BY ROLE ==========
        Map<String, Long> usersByRole = new HashMap<>();
        for (User user : allUsers) {
            if (user.getRoleId() != null) {
                RoleEntity role = roleRepository.findById(user.getRoleId()).orElse(null);
                if (role != null) {
                    String roleCode = role.getCode();
                    usersByRole.put(roleCode, usersByRole.getOrDefault(roleCode, 0L) + 1);
                }
            }
        }
        
        // ========== USERS BY DEPARTMENT ==========
        Map<String, Long> usersByDepartment = new HashMap<>();
        for (User user : allUsers) {
            if (user.getDepartmentId() != null) {
                Department department = departmentRepository.findById(user.getDepartmentId()).orElse(null);
                if (department != null) {
                    String deptName = department.getName();
                    usersByDepartment.put(deptName, usersByDepartment.getOrDefault(deptName, 0L) + 1);
                }
            } else {
                usersByDepartment.put("Chưa có phòng ban", usersByDepartment.getOrDefault("Chưa có phòng ban", 0L) + 1);
            }
        }
        
        // ========== BUILD RESPONSE ==========
        return TenantAnalyticsResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers((int) activeUsers)
                .newUsersThisMonth((int) newUsersThisMonth)
                .usersByRole(usersByRole)
                .usersByDepartment(usersByDepartment)
                .usersCreatedLast7Days((int) usersCreatedLast7Days)
                .usersCreatedLast30Days((int) usersCreatedLast30Days)
                .build();
    }
    
    /**
     * Get all users in tenant
     */
    public List<UserResponse> getAllUsersInTenant(String tenantAdminEmail, String status, Integer roleId) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        UUID tenantId = tenantAdmin.getTenantId();
        StatusFilter filter = StatusFilter.from(status);
        
        log.info("Fetching users for tenant: {}, status={}, roleId={}", tenantId, filter, roleId);
        List<User> users;
        if (isTenantAdmin(tenantAdmin)) {
            users = switch (filter) {
                case ACTIVE -> userRepository.findByTenantIdAndIsActive(tenantId, true);
                case INACTIVE -> userRepository.findByTenantIdAndIsActive(tenantId, false);
                case ALL -> userRepository.findByTenantId(tenantId);
            };
        } else {
            if (tenantAdmin.getDepartmentId() == null) {
                return List.of();
            }
            users = switch (filter) {
                case ACTIVE -> userRepository.findByTenantIdAndDepartmentIdAndIsActive(tenantId, tenantAdmin.getDepartmentId(), true);
                case INACTIVE -> userRepository.findByTenantIdAndDepartmentIdAndIsActive(tenantId, tenantAdmin.getDepartmentId(), false);
                case ALL -> userRepository.findByTenantIdAndDepartmentId(tenantId, tenantAdmin.getDepartmentId());
            };
        }

        if (roleId != null) {
            users = users.stream()
                    .filter(u -> roleId.equals(u.getRoleId()))
                    .collect(Collectors.toList());
        }
        
        return users.stream()
                .map(user -> {
                    RoleEntity role = user.getRoleId() != null ?
                        roleRepository.findById(user.getRoleId()).orElse(null) : null;
                    Department department = user.getDepartmentId() != null ?
                        departmentRepository.findById(user.getDepartmentId()).orElse(null) : null;
                    return mapToUserResponse(user, role, department);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Get user by ID (in same tenant only)
     */
    public UserResponse getUserById(String tenantAdminEmail, UUID userId) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User không tồn tại với ID: " + userId));
        
        // Verify same tenant
        if (!user.getTenantId().equals(tenantAdmin.getTenantId())) {
            throw new AccessDeniedException("Bạn không có quyền truy cập user này!");
        }

        ensureDepartmentScope(tenantAdmin, user, "xem");
        
        RoleEntity role = user.getRoleId() != null ? 
            roleRepository.findById(user.getRoleId()).orElse(null) : null;
        Department department = user.getDepartmentId() != null ? 
            departmentRepository.findById(user.getDepartmentId()).orElse(null) : null;
        
        return mapToUserResponse(user, role, department);
    }
    
    /**
     * Create new user in tenant
     */
    @Transactional
    public UserResponse createUser(String tenantAdminEmail, CreateUserRequest request) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        UUID tenantId = tenantAdmin.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant không tồn tại"));
        
        log.info("Creating new user in tenant: {} ({})", tenant.getName(), tenantId);
        
        // Validate role: TENANT_ADMIN can only create roles within their tenant (not TENANT_ADMIN)
        RoleEntity tenantAdminRole = roleRepository.findByCode("TENANT_ADMIN")
                .orElseThrow(() -> new RuntimeException("Role TENANT_ADMIN không tồn tại"));
        
        RoleEntity selectedRole = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new RuntimeException("Role không tồn tại"));
        
        // Check role belongs to this tenant (for tenant-specific roles) or is system-wide
        if (selectedRole.getTenantId() != null && 
            !selectedRole.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Role không thuộc tenant này");
        }
        
        List<String> forbiddenRoleCodes = List.of("SUPER_ADMIN", "STAFF", "TENANT_ADMIN");
        if (forbiddenRoleCodes.contains(selectedRole.getCode())) {
            throw new RuntimeException(
                "Không thể tạo user với role hệ thống: " + selectedRole.getCode() +
                ". Chỉ được phép gán role EMPLOYEE hoặc custom role của tenant."
            );
        }
        
        // Validate required fields
        if (request.fullName() == null || request.fullName().trim().isEmpty()) {
            throw new RuntimeException("Họ tên không được để trống");
        }
        if (request.contactEmail() == null || request.contactEmail().trim().isEmpty()) {
            throw new RuntimeException("Contact email không được để trống");
        }

        // Validate contactEmail uniqueness
        if (userRepository.existsByContactEmail(request.contactEmail())) {
            throw new IllegalArgumentException(
                "Contact email '" + request.contactEmail() + "' đã được sử dụng bởi tài khoản khác. " +
                "Vui lòng sử dụng email khác."
            );
        }

        // Normalize and validate phoneNumber uniqueness
        String normalizedPhone = null;
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            normalizedPhone = normalizePhoneNumber(request.phoneNumber());
            if (userRepository.existsByPhoneNumber(normalizedPhone)) {
                throw new IllegalArgumentException(
                    "Số điện thoại '" + request.phoneNumber() + "' đã được sử dụng bởi tài khoản khác. " +
                    "Vui lòng sử dụng số điện thoại khác."
                );
            }
        }

        // Validate role and department exist
        RoleEntity role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new RuntimeException("Role không tồn tại"));
        Department department = null;
        if (request.departmentId() != null) {
            department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new RuntimeException("Department không tồn tại"));
        }

        if (!isTenantAdmin(tenantAdmin)) {
            if (request.departmentId() == null || !request.departmentId().equals(tenantAdmin.getDepartmentId())) {
                throw new RuntimeException("Bạn chỉ có thể tạo user trong chính phòng ban của mình");
            }
        }
        
        // Generate login email (email ảo)
        String loginEmail = generateLoginEmail(request.fullName(), tenant);
        log.info("Generated login email: {} for user: {}", loginEmail, request.fullName());
        
        // Generate temporary password
        String temporaryPassword = UserUtil.generateRandomPassword();
        
        // Validate permissions if provided
        if (request.permissions() != null && !request.permissions().isEmpty()) {
            for (String permission : request.permissions()) {
                if (!RolePermissionConstants.isGrantable(permission)) {
                    throw new IllegalArgumentException("Permission '" + permission + "' không thể được cấp");
                }
            }
        }
        
        User newUser = new User();
        newUser.setEmail(loginEmail);  // Email ảo để đăng nhập
        newUser.setContactEmail(request.contactEmail());  // Email thật nhận thông báo
        newUser.setPassword(passwordEncoder.encode(temporaryPassword));
        newUser.setFullName(request.fullName());
        // Set phone number (normalized if provided)
        if (normalizedPhone != null) {
            newUser.setPhoneNumber(normalizedPhone);
        } else {
            newUser.setPhoneNumber(request.phoneNumber());
        }
        newUser.setDateOfBirth(request.dateOfBirth());    // Ngày sinh
        newUser.setAddress(request.address());            // Địa chỉ
        newUser.setRoleId(request.roleId());
        newUser.setDepartmentId(request.departmentId());
        newUser.setTenantId(tenantId);
        newUser.setPermissions(request.permissions());    // Set permissions bổ sung
        newUser.setMustChangePassword(true);  // Bắt buộc đổi mật khẩu lần đầu
        newUser.setCreatedAt(LocalDateTime.now());
        
        User savedUser = userRepository.save(newUser);
        log.info("Created user: {} (login: {}) with roleId: {} in tenant: {}", 
                 savedUser.getFullName(), savedUser.getEmail(), savedUser.getRoleId(), tenantId);
        
        // Send welcome email with credentials to contact email
        try {
            emailService.sendEmployeeWelcome(
                savedUser.getContactEmail(),
                savedUser.getFullName(),
                savedUser.getEmail(),  // Login email (ảo)
                temporaryPassword,
                role.getName(),
                department != null ? department.getName() : "Chưa xác định",
                tenant.getName()
            );
            log.info("Sent welcome email to: {}", savedUser.getContactEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to: {}", savedUser.getContactEmail(), e);
        }
        
        return mapToUserResponse(savedUser, role, department);
    }
    
    /**
     * Generate unique login email for user
     * Format: {username}@{tenantDomain}.com
     * Example: quanph@vintech.com
     */
    private String generateLoginEmail(String fullName, Tenant tenant) {
        // Convert full name to username
        String username = UserUtil.convertFullNameToUsername(fullName);
        
        // Generate tenant domain from company name
        String tenantDomain = UserUtil.removeAccent(tenant.getName())
                .toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("[^a-z0-9]", "");
        
        String baseEmail = username + "@" + tenantDomain + ".com";
        String loginEmail = baseEmail;
        int suffix = 1;
        
        // Handle collision - check if email exists
        while (userRepository.findByEmail(loginEmail).isPresent()) {
            loginEmail = username + suffix + "@" + tenantDomain + ".com";
            suffix++;
            log.info("Email collision detected, trying: {}", loginEmail);
        }
        
        return loginEmail;
    }
    
    /**
     * Update user information
     */
    @Transactional
    public UserResponse updateUser(String tenantAdminEmail, UUID userId, UpdateUserRequest request) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User không tồn tại với ID: " + userId));
        
        // Verify same tenant
        if (!user.getTenantId().equals(tenantAdmin.getTenantId())) {
            throw new AccessDeniedException("Bạn không có quyền cập nhật user này!");
        }

        ensureDepartmentScope(tenantAdmin, user, "cập nhật");
        
        // Cannot change TENANT_ADMIN role
        RoleEntity tenantAdminRole = roleRepository.findByCode("TENANT_ADMIN")
                .orElseThrow(() -> new RuntimeException("Role TENANT_ADMIN không tồn tại"));
        if (user.getRoleId().equals(tenantAdminRole.getId())) {
            throw new RuntimeException("Không thể sửa thông tin TENANT_ADMIN!");
        }
        
        boolean actorIsTenantAdmin = isTenantAdmin(tenantAdmin);

        // Non-admin USER_WRITE can only do basic profile updates in same department
        if (!actorIsTenantAdmin) {
            if (request.roleId() != null || request.departmentId() != null) {
                throw new IllegalArgumentException("Bạn chỉ được cập nhật thông tin cơ bản, không được đổi role/department");
            }
        }

        // Validate role change
        if (request.roleId() != null) {
            RoleEntity newRole = roleRepository.findById(request.roleId())
                    .orElseThrow(() -> new RuntimeException("Role không tồn tại"));
            
            // Check role belongs to this tenant (for tenant-specific roles) or is system-wide
            if (newRole.getTenantId() != null && 
                !newRole.getTenantId().equals(user.getTenantId())) {
                throw new RuntimeException("Role không thuộc tenant này");
            }
            
            RoleEntity tenantAdminRoleCheck = roleRepository.findByCode("TENANT_ADMIN")
                    .orElseThrow(() -> new RuntimeException("Role TENANT_ADMIN không tồn tại"));
            
            List<String> forbiddenRoleCodes = List.of("SUPER_ADMIN", "STAFF", "TENANT_ADMIN");
            if (forbiddenRoleCodes.contains(newRole.getCode())) {
                throw new RuntimeException(
                    "Không thể cập nhật user với role hệ thống: " + newRole.getCode() +
                    ". Chỉ được phép gán role EMPLOYEE hoặc custom role của tenant."
                );
            }
        }
        
        // Update fields
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber());
        }

        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.address() != null) {
            user.setAddress(request.address());
        }
        if (request.departmentId() != null) {
            // Validate department exists
            departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new RuntimeException("Department không tồn tại"));

            if (!actorIsTenantAdmin && !request.departmentId().equals(tenantAdmin.getDepartmentId())) {
                throw new RuntimeException("Bạn chỉ có thể chuyển user về phòng ban của chính bạn");
            }
            user.setDepartmentId(request.departmentId());
        }
        if (request.roleId() != null) {
            // Validate role exists
            roleRepository.findById(request.roleId())
                    .orElseThrow(() -> new RuntimeException("Role không tồn tại"));
            user.setRoleId(request.roleId());
        }
        user.setUpdatedAt(LocalDateTime.now());
        
        User updatedUser = userRepository.save(user);
        log.info("Updated user: {}", userId);
        
        RoleEntity role = user.getRoleId() != null ? 
            roleRepository.findById(user.getRoleId()).orElse(null) : null;
        Department department = user.getDepartmentId() != null ? 
            departmentRepository.findById(user.getDepartmentId()).orElse(null) : null;
        
        return mapToUserResponse(updatedUser, role, department);
    }
    
    /**
     * Delete user
     */
    @Transactional
    public void deleteUser(String tenantAdminEmail, UUID userId) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User không tồn tại với ID: " + userId));
        
        // Verify same tenant
        if (!user.getTenantId().equals(tenantAdmin.getTenantId())) {
            throw new AccessDeniedException("Bạn không có quyền xóa user này!");
        }

        ensureDepartmentScope(tenantAdmin, user, "xóa");
        
        // Cannot delete self
        if (user.getId().equals(tenantAdmin.getId())) {
            throw new RuntimeException("Không thể xóa chính mình!");
        }
        
        // Cannot delete TENANT_ADMIN
        RoleEntity tenantAdminRole = roleRepository.findByCode("TENANT_ADMIN")
                .orElseThrow(() -> new RuntimeException("Role TENANT_ADMIN không tồn tại"));
        if (user.getRoleId().equals(tenantAdminRole.getId())) {
            throw new RuntimeException("Không thể xóa TENANT_ADMIN!");
        }

        Boolean oldActive = user.getIsActive();
        user.setIsActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        writeUserStatusAudit(tenantAdmin, user, "USER_DEACTIVATE", oldActive, user.getIsActive(), "Soft delete user");
        log.info("Soft-deleted user (isActive=false): {}", userId);
    }

    @Transactional
    public UserResponse activateUser(String tenantAdminEmail, UUID userId) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User không tồn tại với ID: " + userId));

        if (!user.getTenantId().equals(tenantAdmin.getTenantId())) {
            throw new AccessDeniedException("Bạn không có quyền cập nhật user này!");
        }

        ensureDepartmentScope(tenantAdmin, user, "kích hoạt");

        Boolean oldActive = user.getIsActive();
        user.setIsActive(true);
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        writeUserStatusAudit(tenantAdmin, saved, "USER_ACTIVATE", oldActive, saved.getIsActive(), "Activate user");
        log.info("Activated user: {}", userId);

        RoleEntity role = saved.getRoleId() != null ? roleRepository.findById(saved.getRoleId()).orElse(null) : null;
        Department department = saved.getDepartmentId() != null ? departmentRepository.findById(saved.getDepartmentId()).orElse(null) : null;
        return mapToUserResponse(saved, role, department);
    }

    @Transactional
    public UserResponse deactivateUser(String tenantAdminEmail, UUID userId) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User không tồn tại với ID: " + userId));

        if (!user.getTenantId().equals(tenantAdmin.getTenantId())) {
            throw new AccessDeniedException("Bạn không có quyền cập nhật user này!");
        }

        ensureDepartmentScope(tenantAdmin, user, "vô hiệu hóa");

        if (user.getId().equals(tenantAdmin.getId())) {
            throw new RuntimeException("Không thể tự vô hiệu hóa chính mình!");
        }

        RoleEntity tenantAdminRole = roleRepository.findByCode("TENANT_ADMIN")
                .orElseThrow(() -> new RuntimeException("Role TENANT_ADMIN không tồn tại"));
        if (user.getRoleId().equals(tenantAdminRole.getId())) {
            throw new RuntimeException("Không thể vô hiệu hóa TENANT_ADMIN!");
        }

        Boolean oldActive = user.getIsActive();
        user.setIsActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        writeUserStatusAudit(tenantAdmin, saved, "USER_DEACTIVATE", oldActive, saved.getIsActive(), "Deactivate user");
        log.info("Deactivated user: {}", userId);

        RoleEntity role = saved.getRoleId() != null ? roleRepository.findById(saved.getRoleId()).orElse(null) : null;
        Department department = saved.getDepartmentId() != null ? departmentRepository.findById(saved.getDepartmentId()).orElse(null) : null;
        return mapToUserResponse(saved, role, department);
    }
    
    /**
     * Reset user password
     */
    @Transactional
    public void resetUserPassword(String tenantAdminEmail, UUID userId) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại với ID: " + userId));
        
        // Verify same tenant
        if (!user.getTenantId().equals(tenantAdmin.getTenantId())) {
            throw new RuntimeException("Bạn không có quyền reset password cho user này!");
        }

        ensureDepartmentScope(tenantAdmin, user, "reset mật khẩu");
        
        // Cannot reset TENANT_ADMIN password
        RoleEntity tenantAdminRole = roleRepository.findByCode("TENANT_ADMIN")
                .orElseThrow(() -> new RuntimeException("Role TENANT_ADMIN không tồn tại"));
        if (user.getRoleId().equals(tenantAdminRole.getId())) {
            throw new RuntimeException("Không thể reset password của TENANT_ADMIN!");
        }
        
        String newPassword = UserUtil.generateRandomPassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true); // Bắt buộc đổi mật khẩu
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        
        log.info("Reset password for user: {}", userId);
        
        // Send new password via email
        try {
            String subject = "Mật khẩu mới của bạn";
            String body = String.format(
                "Xin chào %s,\n\n" +
                "Mật khẩu của bạn đã được reset!\n\n" +
                "Mật khẩu mới: %s\n\n" +
                "Vui lòng đổi mật khẩu sau khi đăng nhập.\n\n" +
                "Trân trọng,\nHệ thống",
                user.getFullName(),
                newPassword
            );
            emailService.sendEmail(user.getContactEmail(), subject, body);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", user.getContactEmail(), e);
            throw new RuntimeException("Không thể gửi email! Vui lòng kiểm tra lại.");
        }
    }
    
    /**
     * Helper: Get user by email
     */
    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User không tồn tại với email: " + email));
    }

    private boolean isTenantAdmin(User actor) {
        if (actor.getRoleId() == null) {
            return false;
        }
        return roleRepository.findById(actor.getRoleId())
                .map(r -> "TENANT_ADMIN".equals(r.getCode()))
                .orElse(false);
    }

    private void ensureDepartmentScope(User actor, User target, String action) {
        if (isTenantAdmin(actor)) {
            return;
        }

        if (actor.getDepartmentId() == null) {
            throw new AccessDeniedException("Tài khoản của bạn chưa gán phòng ban, không thể " + action + " user");
        }

        if (target.getDepartmentId() == null || !actor.getDepartmentId().equals(target.getDepartmentId())) {
            throw new AccessDeniedException("Bạn chỉ có quyền " + action + " user trong cùng phòng ban");
        }
    }

    private void writeUserStatusAudit(User actor, User target, String action, Boolean oldActive, Boolean newActive, String description) {
        AuditLog logEntry = new AuditLog();
        logEntry.setTenantId(actor.getTenantId());
        logEntry.setUserId(actor.getId());
        logEntry.setUserEmail(actor.getEmail());
        logEntry.setAction(action);
        logEntry.setEntityType("User");
        logEntry.setEntityId(String.valueOf(target.getId()));
        logEntry.setOldValue(Map.of("isActive", String.valueOf(oldActive)));
        logEntry.setNewValue(Map.of("isActive", String.valueOf(newActive)));
        logEntry.setDescription(description + " - target=" + target.getEmail());
        logEntry.setStatus("SUCCESS");
        logEntry.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(logEntry);
    }

    private enum StatusFilter {
        ACTIVE,
        INACTIVE,
        ALL;

        static StatusFilter from(String value) {
            if (value == null || value.isBlank()) {
                return ACTIVE;
            }
            try {
                return StatusFilter.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                throw new RuntimeException("status không hợp lệ. Chỉ chấp nhận: ACTIVE, INACTIVE, ALL");
            }
        }
    }
    
    /**
     * Map User entity to UserResponse DTO
     */
    private UserResponse mapToUserResponse(User user, RoleEntity role, Department department) {
        // Get tenant name
        String tenantName = null;
        if (user.getTenantId() != null) {
            tenantName = tenantRepository.findById(user.getTenantId())
                    .map(Tenant::getName)
                    .orElse(null);
        }
        
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getContactEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getDateOfBirth(),
                user.getAddress(),
                user.getRoleId(),
                role != null ? role.getCode() : null,
                role != null ? role.getName() : null,
                department != null ? department.getName() : null,
                tenantName,
                user.getPermissions(),
                user.getIsActive(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt()
        );
    }
    
    /**
     * Update user permissions (TENANT_ADMIN cấp quyền bổ sung cho user)
     */
    @Transactional
    public UserResponse updateUserPermissions(String tenantAdminEmail, UUID userId, List<String> permissions) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại với ID: " + userId));
        
        // Verify same tenant
        if (!user.getTenantId().equals(tenantAdmin.getTenantId())) {
            throw new RuntimeException("Bạn không có quyền cập nhật user này!");
        }

        ensureDepartmentScope(tenantAdmin, user, "cập nhật quyền");
        
        // Cannot change TENANT_ADMIN permissions
        RoleEntity tenantAdminRole = roleRepository.findByCode("TENANT_ADMIN")
                .orElseThrow(() -> new RuntimeException("Role TENANT_ADMIN không tồn tại"));
        if (user.getRoleId().equals(tenantAdminRole.getId())) {
            throw new RuntimeException("Không thể thay đổi quyền của TENANT_ADMIN!");
        }
        
        // Validate all permissions are grantable
        for (String permission : permissions) {
            if (!RolePermissionConstants.isGrantable(permission)) {
                throw new IllegalArgumentException("Permission '" + permission + "' không thể được cấp");
            }
        }
        
        // Update permissions
        user.setPermissions(permissions);
        user.setUpdatedAt(LocalDateTime.now());
        user = userRepository.save(user);
        
        log.info("TENANT_ADMIN {} updated permissions for user {}: {}", 
                tenantAdminEmail, user.getEmail(), permissions);
        
        RoleEntity role = user.getRoleId() != null ? 
            roleRepository.findById(user.getRoleId()).orElse(null) : null;
        Department department = user.getDepartmentId() != null ? 
            departmentRepository.findById(user.getDepartmentId()).orElse(null) : null;
        
        return mapToUserResponse(user, role, department);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return phoneNumber;
        }
        // Convert +84xxxxxxxxx → 0xxxxxxxxx
        if (phoneNumber.startsWith("+84")) {
            return "0" + phoneNumber.substring(3);
        }
        return phoneNumber;
    }
}

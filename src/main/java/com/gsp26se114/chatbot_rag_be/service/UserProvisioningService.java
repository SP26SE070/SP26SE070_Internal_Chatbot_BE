package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.constants.RolePermissionConstants;
import com.gsp26se114.chatbot_rag_be.entity.Department;
import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateUserRequest;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    private static final List<String> FORBIDDEN_ROLE_CODES = List.of("SUPER_ADMIN", "STAFF", "TENANT_ADMIN");

    public record ProvisionedUser(
            User user,
            String temporaryPassword,
            RoleEntity role,
            Department department,
            Tenant tenant
    ) {}

    @Transactional
    public ProvisionedUser provisionUser(User tenantAdmin, CreateUserRequest request) {
        UUID tenantId = tenantAdmin.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant không tồn tại"));

        RoleEntity selectedRole = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new RuntimeException("Role không tồn tại"));

        if (selectedRole.getTenantId() != null && !selectedRole.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Role không thuộc tenant này");
        }
        if (FORBIDDEN_ROLE_CODES.contains(selectedRole.getCode())) {
            throw new RuntimeException(
                    "Không thể tạo user với role hệ thống: " + selectedRole.getCode()
                            + ". Chỉ được phép gán role EMPLOYEE hoặc custom role của tenant."
            );
        }

        if (request.fullName() == null || request.fullName().trim().isEmpty()) {
            throw new RuntimeException("Họ tên không được để trống");
        }
        if (request.contactEmail() == null || request.contactEmail().trim().isEmpty()) {
            throw new RuntimeException("Contact email không được để trống");
        }

        String contactEmail = request.contactEmail().trim();
        if (userRepository.existsByContactEmail(contactEmail)) {
            throw new IllegalArgumentException(
                    "Contact email '" + contactEmail + "' đã được sử dụng bởi tài khoản khác."
            );
        }

        String normalizedPhone = null;
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            normalizedPhone = normalizePhoneNumber(request.phoneNumber());
            if (userRepository.existsByPhoneNumber(normalizedPhone)) {
                throw new IllegalArgumentException(
                        "Số điện thoại '" + request.phoneNumber() + "' đã được sử dụng bởi tài khoản khác."
                );
            }
        }

        Department department = null;
        if (request.departmentId() != null) {
            department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new RuntimeException("Department không tồn tại"));
            if (!department.getTenantId().equals(tenantId)) {
                throw new RuntimeException("Department không thuộc tenant này");
            }
        }

        if (!isTenantAdmin(tenantAdmin)) {
            if (request.departmentId() == null || !request.departmentId().equals(tenantAdmin.getDepartmentId())) {
                throw new RuntimeException("Bạn chỉ có thể tạo user trong chính phòng ban của mình");
            }
        }

        if (request.permissions() != null && !request.permissions().isEmpty()) {
            for (String permission : request.permissions()) {
                if (!RolePermissionConstants.isGrantable(permission)) {
                    throw new IllegalArgumentException("Permission '" + permission + "' không thể được cấp");
                }
            }
        }

        String loginEmail = generateLoginEmail(request.fullName().trim(), tenant);
        String temporaryPassword = UserUtil.generateRandomPassword();

        User newUser = new User();
        newUser.setEmail(loginEmail);
        newUser.setContactEmail(contactEmail);
        newUser.setPassword(passwordEncoder.encode(temporaryPassword));
        newUser.setFullName(request.fullName().trim());
        if (normalizedPhone != null) {
            newUser.setPhoneNumber(normalizedPhone);
        } else if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            newUser.setPhoneNumber(request.phoneNumber().trim());
        }
        newUser.setDateOfBirth(request.dateOfBirth());
        newUser.setAddress(request.address());
        newUser.setRoleId(request.roleId());
        newUser.setDepartmentId(request.departmentId());
        newUser.setTenantId(tenantId);
        newUser.setPermissions(request.permissions());
        newUser.setMustChangePassword(true);
        newUser.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(newUser);
        log.info("Provisioned user: {} (login: {}) in tenant {}", savedUser.getFullName(), savedUser.getEmail(), tenantId);

        return new ProvisionedUser(savedUser, temporaryPassword, selectedRole, department, tenant);
    }

    public String generateLoginEmail(String fullName, Tenant tenant) {
        String username = UserUtil.convertFullNameToUsername(fullName);
        String tenantDomain = UserUtil.removeAccent(tenant.getName())
                .toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("[^a-z0-9]", "");

        String loginEmail = username + "@" + tenantDomain + ".com";
        int suffix = 1;
        while (userRepository.findByEmail(loginEmail).isPresent()) {
            loginEmail = username + suffix + "@" + tenantDomain + ".com";
            suffix++;
        }
        return loginEmail;
    }

    private boolean isTenantAdmin(User actor) {
        if (actor.getRoleId() == null) {
            return false;
        }
        return roleRepository.findById(actor.getRoleId())
                .map(r -> "TENANT_ADMIN".equals(r.getCode()))
                .orElse(false);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return phoneNumber;
        }
        if (phoneNumber.startsWith("+84")) {
            return "0" + phoneNumber.substring(3);
        }
        return phoneNumber.replace("-", "").trim();
    }
}

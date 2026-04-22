package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.exception.ConflictException;
import com.gsp26se114.chatbot_rag_be.exception.ForbiddenException;
import com.gsp26se114.chatbot_rag_be.exception.ResourceNotFoundException;
import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.entity.TenantStatus;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.repository.RoleRepository;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import com.gsp26se114.chatbot_rag_be.util.UserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffTenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public record ApprovalResult(
            Tenant tenant,
            String loginEmail,
            String temporaryPassword
    ) {}

    @Transactional
    public ApprovalResult approveTenant(UUID tenantId, UUID staffUserId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        if (tenant.getStatus() != TenantStatus.PENDING) {
            throw new IllegalStateException("Tenant không ở trạng thái PENDING");
        }

        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setReviewedAt(LocalDateTime.now());
        tenant.setReviewedBy(staffUserId);
        tenantRepository.save(tenant);

        // Create TENANT_ADMIN user for this tenant
        String representativeName = tenant.getRepresentativeName() != null
                ? tenant.getRepresentativeName()
                : tenant.getContactEmail();

        RoleEntity tenantAdminRole = roleRepository.findByCode("TENANT_ADMIN")
                .orElseThrow(() -> new RuntimeException("Role TENANT_ADMIN không tồn tại"));

        String loginEmail = generateTenantAdminLoginEmail(tenant.getName());
        String temporaryPassword = UserUtil.generateRandomPassword();

        User tenantAdminUser = new User();
        tenantAdminUser.setEmail(loginEmail);
        tenantAdminUser.setContactEmail(tenant.getContactEmail());
        tenantAdminUser.setPassword(passwordEncoder.encode(temporaryPassword));
        tenantAdminUser.setFullName(representativeName);
        tenantAdminUser.setPhoneNumber(tenant.getRepresentativePhone());
        tenantAdminUser.setRoleId(tenantAdminRole.getId());
        tenantAdminUser.setTenantId(tenant.getId());
        tenantAdminUser.setMustChangePassword(true);
        tenantAdminUser.setIsActive(true);
        tenantAdminUser.setCreatedAt(LocalDateTime.now());

        userRepository.save(tenantAdminUser);

        return new ApprovalResult(tenant, loginEmail, temporaryPassword);
    }

    @Transactional
    public ApprovalResult resendTenantAdminCredentials(UUID tenantId, UUID staffUserId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        if (tenant.getStatus() != TenantStatus.ACTIVE && tenant.getStatus() != TenantStatus.SUSPENDED) {
            throw new ForbiddenException("Chỉ có thể gửi lại thông tin đăng nhập cho tenant ACTIVE hoặc SUSPENDED");
        }

        RoleEntity tenantAdminRole = roleRepository.findByCode("TENANT_ADMIN")
                .orElseThrow(() -> new RuntimeException("Role TENANT_ADMIN không tồn tại"));

        User tenantAdminUser = userRepository
                .findFirstByTenantIdAndRoleIdAndIsActive(tenant.getId(), tenantAdminRole.getId(), true)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản TENANT_ADMIN đang hoạt động của tenant"));

        String temporaryPassword = UserUtil.generateRandomPassword();
        tenantAdminUser.setPassword(passwordEncoder.encode(temporaryPassword));
        tenantAdminUser.setMustChangePassword(true);
        tenantAdminUser.setUpdatedAt(LocalDateTime.now());
        userRepository.save(tenantAdminUser);

        tenant.setReviewedAt(LocalDateTime.now());
        tenant.setReviewedBy(staffUserId);
        tenantRepository.save(tenant);

        return new ApprovalResult(tenant, tenantAdminUser.getEmail(), temporaryPassword);
    }

    @Transactional
    public Tenant rejectTenant(UUID tenantId, UUID staffUserId, String reason) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        if (tenant.getStatus() != TenantStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể từ chối tenant đang ở trạng thái PENDING");
        }

        tenant.setStatus(TenantStatus.REJECTED);
        tenant.setReviewedAt(LocalDateTime.now());
        tenant.setReviewedBy(staffUserId);
        tenant.setRejectionReason(reason);
        tenantRepository.save(tenant);

        return tenant;
    }

    @Transactional
    public Tenant markTenantForDeletion(UUID tenantId, UUID staffUserId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tenant"));

        if (Boolean.TRUE.equals(tenant.getMarkedForDeletion())) {
            throw new ConflictException("Tenant đã được đánh dấu xóa trước đó");
        }

        LocalDateTime now = LocalDateTime.now();
        tenant.setMarkedForDeletion(true);
        tenant.setInactiveAt(tenant.getInactiveAt() != null ? tenant.getInactiveAt() : now);
        tenant.setUpdatedAt(now);
        tenant.setReviewedAt(now);
        tenant.setReviewedBy(staffUserId);

        if (tenant.getStatus() != TenantStatus.REJECTED) {
            tenant.setStatus(TenantStatus.SUSPENDED);
        }

        tenantRepository.save(tenant);
        return tenant;
    }

    private String generateTenantAdminLoginEmail(String tenantName) {
        String username = "admin";
        String tenantDomain = UserUtil.removeAccent(tenantName)
                .toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("[^a-z0-9]", "");

        String baseEmail = username + "@" + tenantDomain + ".com";
        String loginEmail = baseEmail;
        int suffix = 1;

        while (userRepository.existsByEmail(loginEmail)) {
            loginEmail = username + suffix + "@" + tenantDomain + ".com";
            suffix++;
        }

        return loginEmail;
    }
}

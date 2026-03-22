package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByResetPasswordToken(String token);

    Optional<User> findByPasswordResetSessionToken(String passwordResetSessionToken);
    boolean existsByContactEmail(String contactEmail);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    List<User> findByTenantId(UUID tenantId);
    List<User> findByTenantIdAndIsActive(UUID tenantId, Boolean isActive);
    List<User> findByTenantIdAndDepartmentId(UUID tenantId, Integer departmentId);
    List<User> findByTenantIdAndDepartmentIdAndIsActive(UUID tenantId, Integer departmentId, Boolean isActive);
    List<User> findByRoleId(Integer roleId);
    long countByRoleId(Integer roleId);
    long countByTenantId(UUID tenantId);
}
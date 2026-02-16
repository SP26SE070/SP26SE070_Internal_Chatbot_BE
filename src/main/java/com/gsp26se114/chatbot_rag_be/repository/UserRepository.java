package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByResetPasswordToken(String token);
    boolean existsByContactEmail(String contactEmail);
    boolean existsByEmail(String email);
    boolean existsByEmployeeCode(String employeeCode);
    List<User> findByTenantId(UUID tenantId);
    List<User> findByRoleId(Integer roleId);
    long countByRoleId(Integer roleId);
    long countByTenantId(UUID tenantId);
}
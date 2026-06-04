package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.EmployeeImportSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeImportSessionRepository extends JpaRepository<EmployeeImportSession, UUID> {

    Optional<EmployeeImportSession> findByIdAndTenantId(UUID id, UUID tenantId);
}

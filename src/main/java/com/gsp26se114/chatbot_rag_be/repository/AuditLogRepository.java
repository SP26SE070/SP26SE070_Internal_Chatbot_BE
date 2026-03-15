package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}

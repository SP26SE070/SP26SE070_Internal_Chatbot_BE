package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    // Per-user conversations, ordered by last message
    Page<ChatSession> findByUserIdOrderByLastMessageAtDesc(UUID userId, Pageable pageable);

    // Per-tenant all conversations, ordered by last message (for tenant admin)
    Page<ChatSession> findByTenantIdOrderByLastMessageAtDesc(UUID tenantId, Pageable pageable);

    // Get session only if owned by user (for non-admin access)
    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);

    // Get session only if within tenant (for admin access)
    Optional<ChatSession> findByIdAndTenantId(UUID id, UUID tenantId);

    // Count active sessions for a user
    Long countByUserIdAndStatus(UUID userId, String status);

    // Check if session exists for user
    boolean existsByIdAndUserId(UUID id, UUID userId);
}

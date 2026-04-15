package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.ChatbotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatbotConfigRepository extends JpaRepository<ChatbotConfig, UUID> {
    Optional<ChatbotConfig> findByTenantId(UUID tenantId);
}

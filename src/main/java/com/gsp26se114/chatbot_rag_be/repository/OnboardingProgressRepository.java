package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.OnboardingProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingProgressRepository extends JpaRepository<OnboardingProgress, UUID> {

    Optional<OnboardingProgress> findByUserIdAndModuleId(UUID userId, UUID moduleId);

    List<OnboardingProgress> findByUserIdAndTenantId(UUID userId, UUID tenantId);
}

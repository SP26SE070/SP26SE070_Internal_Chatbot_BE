package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.OnboardingModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingModuleRepository extends JpaRepository<OnboardingModule, UUID> {

    List<OnboardingModule> findByIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc();

    List<OnboardingModule> findByOrderByDisplayOrderAscCreatedAtAsc();

    List<OnboardingModule> findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCreatedAtAsc(UUID tenantId);

    List<OnboardingModule> findByTenantIdOrderByDisplayOrderAscCreatedAtAsc(UUID tenantId);

    Optional<OnboardingModule> findByIdAndTenantId(UUID id, UUID tenantId);
}

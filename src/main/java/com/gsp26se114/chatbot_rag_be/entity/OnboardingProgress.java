package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "onboarding_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uq_onboarding_progress_user_module", columnNames = {"user_id", "module_id"})
}, indexes = {
        @Index(name = "idx_onboarding_progress_tenant_user", columnList = "tenant_id, user_id"),
        @Index(name = "idx_onboarding_progress_module", columnList = "module_id")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OnboardingProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "onboarding_progress_id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(name = "read_percent", nullable = false)
    private Integer readPercent = 0;

    @Column(nullable = false)
    private Boolean completed = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

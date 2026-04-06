package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "onboarding_modules", indexes = {
        @Index(name = "idx_onboarding_modules_tenant_active", columnList = "tenant_id, is_active"),
        @Index(name = "idx_onboarding_modules_tenant_order", columnList = "tenant_id, display_order")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OnboardingModule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "onboarding_module_id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "detail_file_name", length = 255)
    private String detailFileName;

    @Column(name = "detail_file_type", length = 100)
    private String detailFileType;

    @Column(name = "detail_file_path", length = 500)
    private String detailFilePath;

    @Column(name = "detail_file_size")
    private Long detailFileSize;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes = 5;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_permissions", columnDefinition = "jsonb")
    private List<String> requiredPermissions = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

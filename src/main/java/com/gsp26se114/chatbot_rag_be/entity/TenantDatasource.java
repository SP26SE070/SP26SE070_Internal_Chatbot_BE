package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants_datasources")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TenantDatasource {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "datasource_url", nullable = false, length = 500)
    private String datasourceUrl;

    @Column(name = "datasource_username", nullable = false)
    private String datasourceUsername;

    @Column(name = "datasource_password", nullable = false)
    private String datasourcePassword;

    @Column(name = "datasource_type", nullable = false, length = 20)
    private String datasourceType; // PRIMARY or ENTERPRISE

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

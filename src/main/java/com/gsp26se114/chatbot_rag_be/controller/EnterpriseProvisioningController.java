package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.repository.TenantDatasourceRepository;
import com.gsp26se114.chatbot_rag_be.service.EnterpriseBackupService;
import com.gsp26se114.chatbot_rag_be.service.EnterpriseProvisioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/enterprise-tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin: Enterprise Provisioning", description = "Super Admin endpoints to provision dedicated Enterprise databases")
public class EnterpriseProvisioningController {

    private final EnterpriseProvisioningService provisioningService;
    private final TenantDatasourceRepository tenantDatasourceRepository;
    private final EnterpriseBackupService backupService;

    @PostMapping("/{tenantId}/provision")
    @Operation(summary = "Provision a dedicated Enterprise database for a tenant",
            description = "Creates a dedicated PostgreSQL database on the Enterprise VPS, initializes schema, registers the datasource, sets tier to ENTERPRISE, and refreshes routing.")
    public ResponseEntity<?> provision(@PathVariable UUID tenantId) {
        try {
            EnterpriseProvisioningService.ProvisioningResult result =
                    provisioningService.provisionEnterpriseTenant(tenantId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("tenantId", result.tenantId().toString());
            response.put("databaseName", result.databaseName());
            response.put("tier", result.tier());
            response.put("newlyCreated", result.newlyCreated());
            response.put("message", "Enterprise database provisioned successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Provisioning failed for tenant {}: {}", tenantId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("tenantId", tenantId.toString());
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping
    @Operation(summary = "List all provisioned Enterprise tenants")
    public ResponseEntity<?> listEnterpriseTenants() {
        var datasources = tenantDatasourceRepository.findAllByIsActiveTrue();
        return ResponseEntity.ok(datasources.stream().map(ds -> {
            Map<String, Object> m = new HashMap<>();
            m.put("tenantId", ds.getTenantId().toString());
            m.put("datasourceType", ds.getDatasourceType());
            m.put("datasourceUrl", ds.getDatasourceUrl());
            m.put("isActive", ds.getIsActive());
            return m;
        }).toList());
    }

    @GetMapping("/{tenantId}/backup")
    @Operation(summary = "Backup a tenant's Enterprise database",
            description = "Runs pg_dump on the tenant's dedicated database and returns a .sql file the customer can restore on their own infrastructure.")
    public ResponseEntity<?> backup(@PathVariable UUID tenantId) {
        try {
            EnterpriseBackupService.BackupResult result = backupService.backupTenant(tenantId);
            FileSystemResource resource = new FileSystemResource(result.filePath().toFile());
            String filename = result.databaseName() + "_backup.sql";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(result.sizeBytes())
                    .body(resource);
        } catch (EnterpriseBackupService.BackupUnavailableException e) {
            log.warn("Backup unavailable for tenant {}: {}", tenantId, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("tenantId", tenantId.toString());
            error.put("error", e.getMessage());
            return ResponseEntity.status(503).body(error);
        } catch (Exception e) {
            log.error("Backup failed for tenant {}: {}", tenantId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("tenantId", tenantId.toString());
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

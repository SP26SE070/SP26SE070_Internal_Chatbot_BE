package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.repository.TenantDatasourceRepository;
import com.gsp26se114.chatbot_rag_be.service.EnterpriseBackupService;
import com.gsp26se114.chatbot_rag_be.service.EnterpriseProvisioningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnterpriseProvisioningControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private EnterpriseProvisioningService provisioningService;
    @Mock private TenantDatasourceRepository tenantDatasourceRepository;
    @Mock private EnterpriseBackupService backupService;

    @Test
    void backupReturnsSqlAttachmentWhenServiceSucceeds() throws Exception {
        Path backupFile = Files.createTempFile("enterprise_backup_test_", ".sql");
        Files.writeString(backupFile, "select 1;", StandardCharsets.UTF_8);
        when(backupService.backupTenant(TENANT_ID))
                .thenReturn(new EnterpriseBackupService.BackupResult(
                        TENANT_ID, "enterprise_demo", backupFile, Files.size(backupFile)));

        EnterpriseProvisioningController controller = controller();
        ResponseEntity<?> response = controller.backup(TENANT_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"enterprise_demo_backup.sql\"");
        assertThat(response.getBody()).isInstanceOf(FileSystemResource.class);
    }

    @Test
    void backupUnavailableReturnsReadable503InsteadOfUgly500() {
        String message = "Enterprise backup is available only on the admin/VPS environment because pg_dump runtime is not available in this deployment.";
        when(backupService.backupTenant(TENANT_ID))
                .thenThrow(new EnterpriseBackupService.BackupUnavailableException(message));

        EnterpriseProvisioningController controller = controller();
        ResponseEntity<?> response = controller.backup(TENANT_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("success", false);
        assertThat(body).containsEntry("tenantId", TENANT_ID.toString());
        assertThat(body.get("error")).isEqualTo(message);
    }

    private EnterpriseProvisioningController controller() {
        return new EnterpriseProvisioningController(
                provisioningService,
                tenantDatasourceRepository,
                backupService
        );
    }
}

package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.repository.TenantDatasourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Creates a pg_dump backup of a tenant's dedicated Enterprise database.
 * Runs pg_dump inside the existing PostgreSQL Docker container, connecting to the
 * Enterprise VPS through the tunnel (host.docker.internal:5435).
 * Returns a .sql file the customer can restore on their own infrastructure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnterpriseBackupService {

    public static class BackupUnavailableException extends RuntimeException {
        public BackupUnavailableException(String message) {
            super(message);
        }

        public BackupUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final String RUNTIME_UNAVAILABLE_MESSAGE =
            "Enterprise backup is available only on the admin/VPS environment because pg_dump runtime is not available in this deployment.";

    @Value("${backup.docker-container:chatbot-postgres}")
    private String dockerContainer;

    @Value("${backup.pg-host:host.docker.internal}")
    private String pgHost;

    @Value("${backup.pg-port:5435}")
    private String pgPort;

    @Value("${enterprise.vps.admin-username}")
    private String pgUser;

    @Value("${enterprise.vps.admin-password}")
    private String pgPassword;

    private final TenantDatasourceRepository tenantDatasourceRepository;

    public record BackupResult(UUID tenantId, String databaseName, Path filePath, long sizeBytes) {}

    public BackupResult backupTenant(UUID tenantId) {
        var ds = tenantDatasourceRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new BackupUnavailableException(
                        "No active enterprise datasource for tenant " + tenantId));

        String dbName = extractDatabaseName(ds.getDatasourceUrl());
        log.info("Backing up enterprise database {} for tenant {}", dbName, tenantId);

        try {
            Path outFile = Files.createTempFile("backup_" + dbName + "_", ".sql");
            Path errFile = Files.createTempFile("backup_err_", ".log");

            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "exec",
                    "-e", "PGPASSWORD=" + pgPassword,
                    dockerContainer,
                    "pg_dump",
                    "-h", pgHost,
                    "-p", pgPort,
                    "-U", pgUser,
                    "-d", dbName,
                    "--no-owner", "--no-privileges"
            );
            pb.redirectOutput(outFile.toFile());
            pb.redirectError(errFile.toFile());

            Process process = pb.start();
            int exit = process.waitFor();

            if (exit != 0) {
                String err = sanitizeProcessError(Files.readString(errFile));
                Files.deleteIfExists(outFile);
                Files.deleteIfExists(errFile);
                throw new BackupUnavailableException("pg_dump failed (exit " + exit + "): " + err);
            }
            Files.deleteIfExists(errFile);

            long size = Files.size(outFile);
            log.info("Backup complete for {}: {} bytes", dbName, size);
            return new BackupResult(tenantId, dbName, outFile, size);

        } catch (IOException e) {
            throw new BackupUnavailableException(RUNTIME_UNAVAILABLE_MESSAGE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackupUnavailableException("Backup interrupted for tenant " + tenantId, e);
        }
    }

    private String sanitizeProcessError(String error) {
        if (error == null || error.isBlank()) {
            return "pg_dump command did not return diagnostic output";
        }
        return error
                .replace(pgPassword, "[REDACTED]")
                .replaceAll("(?i)(password=)[^\\s]+", "$1[REDACTED]")
                .replaceAll("(?i)(PGPASSWORD=)[^\\s]+", "$1[REDACTED]")
                .trim();
    }

    /**
     * Extract database name from a JDBC URL like
     * jdbc:postgresql://host:port/dbname?params
     */
    private String extractDatabaseName(String jdbcUrl) {
        String afterSlash = jdbcUrl.substring(jdbcUrl.lastIndexOf('/') + 1);
        int q = afterSlash.indexOf('?');
        return q >= 0 ? afterSlash.substring(0, q) : afterSlash;
    }
}

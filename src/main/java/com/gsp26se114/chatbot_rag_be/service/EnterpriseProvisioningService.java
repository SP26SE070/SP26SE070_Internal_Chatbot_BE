package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.config.DataSourceConfig.EnterpriseDataSourceInitializer;
import com.gsp26se114.chatbot_rag_be.entity.SubscriptionTier;
import com.gsp26se114.chatbot_rag_be.entity.TenantDatasource;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import com.gsp26se114.chatbot_rag_be.repository.TenantDatasourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provisions a dedicated PostgreSQL database on the Enterprise VPS for a tenant.
 * Flow: CREATE DATABASE -> run captured schema (tables + pgvector + halfvec index).
 * The schema file is captured from a working enterprise DB (enterprise-schema.sql).
 */
@Service
@Slf4j
public class EnterpriseProvisioningService {

    public record ProvisioningResult(
            UUID tenantId,
            String databaseName,
            String databaseUrl,
            String tier,
            boolean newlyCreated
    ) {
    }

    @Value("${enterprise.vps.admin-url}")
    private String adminUrl;

    @Value("${enterprise.vps.admin-username}")
    private String adminUsername;

    @Value("${enterprise.vps.admin-password}")
    private String adminPassword;

    @Value("${enterprise.vps.db-url-template}")
    private String dbUrlTemplate;

    @Value("${enterprise.vps.db-name-prefix}")
    private String dbNamePrefix;

    @Autowired
    private TenantDatasourceRepository tenantDatasourceRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private EnterpriseDataSourceInitializer enterpriseDataSourceRefresher;

    /**
     * Build a safe database name from a tenant UUID (no dashes).
     * Example: enterprise_550e8400_e29b_41d4_a716_446655440000
     */
    public String buildDatabaseName(UUID tenantId) {
        return dbNamePrefix + tenantId.toString().replace("-", "_");
    }

    /**
     * Build the JDBC URL for a tenant's enterprise database.
     */
    public String buildDatabaseUrl(UUID tenantId) {
        return String.format(dbUrlTemplate, buildDatabaseName(tenantId));
    }

    /**
     * Creates the database (if not exists) and initializes the schema.
     * Returns the JDBC URL of the provisioned database.
     */
    public String provisionDatabase(UUID tenantId) {
        String dbName = buildDatabaseName(tenantId);
        log.info("Provisioning enterprise database: {}", dbName);

        // Step 1: CREATE DATABASE via admin connection
        createDatabaseIfNotExists(dbName);

        // Step 2: Connect to the new database and run the schema
        String newDbUrl = buildDatabaseUrl(tenantId);
        initializeSchema(newDbUrl);

        log.info("Enterprise database provisioned: {} -> {}", dbName, newDbUrl);
        return newDbUrl;
    }

    /**
     * Full provisioning: create VPS DB + schema, register datasource, set tier ENTERPRISE,
     * refresh routing. Each persistence step commits before the datasource refresh so the
     * reload sees the new row.
     */
    public ProvisioningResult provisionEnterpriseTenant(UUID tenantId) {
        // 1. Validate an active subscription exists
        var subscription = subscriptionRepository.findActiveSubscriptionByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException(
                        "No active subscription found for tenant " + tenantId));

        // 2. Determine if already provisioned
        boolean alreadyHasDatasource = tenantDatasourceRepository.existsByTenantId(tenantId);

        // 3. Create the database + schema on the VPS (idempotent: skips if DB exists)
        String dbUrl = provisionDatabase(tenantId);
        String dbName = buildDatabaseName(tenantId);

        // 4. Insert or update the tenants_datasources row
        var existing = tenantDatasourceRepository.findByTenantIdAndIsActiveTrue(tenantId);
        TenantDatasource ds = existing.orElseGet(TenantDatasource::new);
        ds.setTenantId(tenantId);
        ds.setDatasourceUrl(dbUrl);
        ds.setDatasourceUsername(adminUsername);
        ds.setDatasourcePassword(adminPassword);
        ds.setDatasourceType("ENTERPRISE");
        ds.setIsActive(true);
        tenantDatasourceRepository.save(ds);

        // 5. Set subscription tier to ENTERPRISE
        subscription.setTier(SubscriptionTier.ENTERPRISE);
        subscriptionRepository.save(subscription);

        // 6. Refresh routing datasources so the new DB is live immediately
        enterpriseDataSourceRefresher.loadEnterpriseDataSources();

        log.info("Enterprise tenant provisioned: {} -> {} (tier=ENTERPRISE)", tenantId, dbName);
        return new ProvisioningResult(tenantId, dbName, dbUrl, "ENTERPRISE", !alreadyHasDatasource);
    }

    private void createDatabaseIfNotExists(String dbName) {
        try (Connection conn = DriverManager.getConnection(adminUrl, adminUsername, adminPassword)) {
            // Check if DB already exists
            boolean exists;
            try (var ps = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
                ps.setString(1, dbName);
                try (var rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (exists) {
                log.warn("Database {} already exists; skipping CREATE DATABASE.", dbName);
                return;
            }
            // CREATE DATABASE cannot use parameters; dbName is sanitized (alphanumeric + underscore)
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE DATABASE \"" + dbName + "\"");
            }
            log.info("Created database: {}", dbName);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create database " + dbName + ": " + e.getMessage(), e);
        }
    }

    private void initializeSchema(String dbUrl) {
        try (Connection conn = DriverManager.getConnection(dbUrl, adminUsername, adminPassword)) {
            // Load schema file, strip psql meta-commands that JDBC can't run.
            String rawSchema = loadSchemaFile();
            String cleanedSchema = stripPsqlMetaCommands(rawSchema);

            ByteArrayResource resource = new ByteArrayResource(
                    cleanedSchema.getBytes(StandardCharsets.UTF_8));

            ScriptUtils.executeSqlScript(conn, resource);
            log.info("Schema initialized on database via {}", dbUrl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize schema on " + dbUrl + ": " + e.getMessage(), e);
        }
    }

    private String loadSchemaFile() throws Exception {
        ClassPathResource resource = new ClassPathResource("enterprise-schema.sql");
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Removes psql-only meta-commands (lines starting with backslash like \restrict)
     * that are invalid when executed through JDBC.
     */
    private String stripPsqlMetaCommands(String sql) {
        return sql.lines()
                .filter(line -> !line.trim().startsWith("\\"))
                .collect(Collectors.joining("\n"));
    }
}

package com.gsp26se114.chatbot_rag_be.config;

import com.gsp26se114.chatbot_rag_be.entity.TenantDatasource;
import com.gsp26se114.chatbot_rag_be.repository.TenantDatasourceRepository;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configures multi-tenant datasource routing.
 *
 * Default datasource: Railway PostgreSQL (for Starter/Standard/Trial tenants)
 * Enterprise datasources: loaded from tenants_datasources table at startup
 */
@Configuration
@Slf4j
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties defaultDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource defaultDataSource(
            @Qualifier("defaultDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource ds = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        ds.setPoolName("default-pool");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        return ds;
    }

    @Bean
    @Primary
    public DataSource routingDataSource(DataSource defaultDataSource) {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("DEFAULT", defaultDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(defaultDataSource);

        return routingDataSource;
    }

    /**
     * After application starts, load enterprise datasources from the database.
     * This runs after all beans are initialized so TenantDatasourceRepository is available.
     */
    @Bean
    public EnterpriseDataSourceInitializer enterpriseDataSourceInitializer(
            DataSource routingDataSource,
            TenantDatasourceRepository tenantDatasourceRepository) {
        return new EnterpriseDataSourceInitializer(routingDataSource, tenantDatasourceRepository);
    }

    @Slf4j
    public static class EnterpriseDataSourceInitializer {

        private final DataSource routingDataSource;
        private final TenantDatasourceRepository repository;

        public EnterpriseDataSourceInitializer(DataSource routingDataSource,
                                               TenantDatasourceRepository repository) {
            this.routingDataSource = routingDataSource;
            this.repository = repository;
        }

        @PostConstruct
        public void loadEnterpriseDataSources() {
            try {
                List<TenantDatasource> enterprises = repository.findAllByIsActiveTrue();

                if (enterprises.isEmpty()) {
                    log.info("No enterprise datasources found. All tenants will use default database.");
                    return;
                }

                TenantRoutingDataSource routing = (TenantRoutingDataSource) routingDataSource;

                Map<Object, Object> current = new HashMap<>();
                current.put("DEFAULT", routing.getDefaultDataSource());

                for (TenantDatasource td : enterprises) {
                    try {
                        HikariDataSource enterpriseDs = new HikariDataSource();
                        enterpriseDs.setJdbcUrl(td.getDatasourceUrl());
                        enterpriseDs.setUsername(td.getDatasourceUsername());
                        enterpriseDs.setPassword(td.getDatasourcePassword());
                        enterpriseDs.setDriverClassName("org.postgresql.Driver");
                        enterpriseDs.setPoolName("enterprise-" + td.getTenantId());
                        enterpriseDs.setMaximumPoolSize(5);
                        enterpriseDs.setMinimumIdle(1);
                        enterpriseDs.setConnectionTimeout(5000);

                        current.put(td.getTenantId().toString(), enterpriseDs);
                        log.info("Loaded enterprise datasource for tenant: {}", td.getTenantId());
                    } catch (Exception e) {
                        log.error("Failed to load enterprise datasource for tenant {}: {}",
                                td.getTenantId(), e.getMessage());
                    }
                }

                routing.setTargetDataSources(current);
                routing.afterPropertiesSet();

                log.info("Loaded {} enterprise datasource(s)", enterprises.size());

            } catch (Exception e) {
                log.warn("Could not load enterprise datasources (table may not exist yet): {}",
                        e.getMessage());
            }
        }
    }
}

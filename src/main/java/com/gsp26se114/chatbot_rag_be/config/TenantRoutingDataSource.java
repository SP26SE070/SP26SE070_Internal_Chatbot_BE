package com.gsp26se114.chatbot_rag_be.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Routes database queries to the correct datasource based on tenant context.
 *
 * Routing logic:
 * - If no tenant in context (public endpoints like login) -> use default (Railway)
 * - If tenant tier is STARTER/STANDARD/TRIAL -> use default (Railway shared DB)
 * - If tenant tier is ENTERPRISE -> use dedicated enterprise datasource
 *
 * The lookup key is either "DEFAULT" or the tenant UUID string.
 */
@Slf4j
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private static final String DEFAULT_DATASOURCE = "DEFAULT";

    public DataSource getDefaultDataSource() {
        return getResolvedDefaultDataSource();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        UUID tenantId = TenantContext.getCurrentTenant();
        String tier = TenantContext.getCurrentTier();

        // No tenant context (login, registration, public endpoints)
        if (tenantId == null || tier == null) {
            return DEFAULT_DATASOURCE;
        }

        // Enterprise tenants get their own datasource
        if ("ENTERPRISE".equals(tier)) {
            String key = tenantId.toString();
            log.debug("Routing to enterprise datasource: {}", key);
            return key;
        }

        // All other tiers use default shared datasource
        return DEFAULT_DATASOURCE;
    }
}

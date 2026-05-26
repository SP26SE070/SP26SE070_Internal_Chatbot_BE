package com.gsp26se114.chatbot_rag_be.config;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * ThreadLocal holder for the current tenant ID.
 * Set by TenantContextFilter on each request.
 * Read by TenantRoutingDataSource to route queries.
 * Cleared after each request to prevent leaks.
 */
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TIER = new ThreadLocal<>();

    public static void setCurrentTenant(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTier(String tier) {
        CURRENT_TIER.set(tier);
    }

    public static String getCurrentTier() {
        return CURRENT_TIER.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_TIER.remove();
    }

    public static <T> T withDefaultDataSource(Supplier<T> action) {
        UUID savedTenant = CURRENT_TENANT.get();
        String savedTier = CURRENT_TIER.get();
        try {
            CURRENT_TENANT.remove();
            CURRENT_TIER.remove();
            return action.get();
        } finally {
            if (savedTenant != null) CURRENT_TENANT.set(savedTenant);
            if (savedTier != null) CURRENT_TIER.set(savedTier);
        }
    }

    public static void runOnDefaultDataSource(Runnable action) {
        withDefaultDataSource(() -> {
            action.run();
            return null;
        });
    }
}

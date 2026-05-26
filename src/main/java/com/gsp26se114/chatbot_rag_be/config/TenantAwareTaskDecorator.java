package com.gsp26se114.chatbot_rag_be.config;

import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

import java.util.UUID;

/**
 * Captures TenantContext from the request thread and restores it on the
 * async worker thread, so async repository calls route to the correct database.
 *
 * Without this, @Async methods run with no tenant context and default to Main DB,
 * breaking multi-tenant routing for background work like document embedding.
 */
public class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        UUID tenantId = TenantContext.getCurrentTenant();
        String tier = TenantContext.getCurrentTier();

        return () -> {
            try {
                if (tenantId != null) {
                    TenantContext.setCurrentTenant(tenantId);
                }
                if (tier != null) {
                    TenantContext.setCurrentTier(tier);
                }
                runnable.run();
            } finally {
                TenantContext.clear();
            }
        };
    }
}

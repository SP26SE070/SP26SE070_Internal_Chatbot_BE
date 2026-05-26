package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.config.TenantContext;
import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.entity.SubscriptionTier;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the subscription tier for a tenant.
 * Used by routing logic to determine which database to use.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantTierResolver {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Returns tier name: "TRIAL", "STARTER", "STANDARD", "ENTERPRISE"
     * Returns "STARTER" as default if no active subscription found.
     */
    public String resolveTier(UUID tenantId) {
        if (tenantId == null) {
            return "STARTER";
        }
        return TenantContext.withDefaultDataSource(() -> {
            Optional<Subscription> sub = subscriptionRepository
                    .findActiveSubscriptionByTenantId(tenantId);
            return sub.map(s -> s.getTier().name()).orElse("STARTER");
        });
    }

    /**
     * Returns true if tenant is Enterprise tier.
     */
    public boolean isEnterprise(UUID tenantId) {
        return "ENTERPRISE".equals(resolveTier(tenantId));
    }
}

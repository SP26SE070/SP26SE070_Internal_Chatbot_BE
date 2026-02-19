package com.gsp26se114.chatbot_rag_be.payload.request;

import com.gsp26se114.chatbot_rag_be.entity.BillingCycle;
import com.gsp26se114.chatbot_rag_be.entity.SubscriptionTier;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for selecting a subscription plan
 *
 * @author GSP26SE114
 * @version 1.0
 */
@Data
public class SelectPlanRequest {

    /**
     * Subscription tier to purchase (STARTER, STANDARD, ENTERPRISE)
     * Cannot select TRIAL via this endpoint
     */
    @NotNull(message = "Tier is required")
    private SubscriptionTier tier;

    /**
     * Billing cycle (MONTHLY, QUARTERLY, YEARLY)
     */
    @NotNull(message = "Billing cycle is required")
    private BillingCycle cycle;
}

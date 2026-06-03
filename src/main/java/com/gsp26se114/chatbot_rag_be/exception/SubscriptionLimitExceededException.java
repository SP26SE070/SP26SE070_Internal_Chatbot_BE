package com.gsp26se114.chatbot_rag_be.exception;

import java.util.UUID;

public class SubscriptionLimitExceededException extends RuntimeException {

    private final String limitName;
    private final Long currentUsage;
    private final Long limit;

    public SubscriptionLimitExceededException(String limitName, long currentUsage, long limit) {
        super("Subscription limit exceeded for " + limitName + ": current=" + currentUsage + ", limit=" + limit);
        this.limitName = limitName;
        this.currentUsage = currentUsage;
        this.limit = limit;
    }

    private SubscriptionLimitExceededException(String limitName, String message) {
        super(message);
        this.limitName = limitName;
        this.currentUsage = null;
        this.limit = null;
    }

    public static SubscriptionLimitExceededException noActiveSubscription(UUID tenantId) {
        return new SubscriptionLimitExceededException(
                "active_subscription",
                "No ACTIVE subscription found for tenant " + tenantId + "; operation is not allowed.");
    }

    public static SubscriptionLimitExceededException missingLimit(String limitName, UUID tenantId) {
        return new SubscriptionLimitExceededException(
                limitName,
                "ACTIVE subscription for tenant " + tenantId
                        + " is missing required limit " + limitName
                        + "; operation is not allowed.");
    }

    public String getLimitName() {
        return limitName;
    }

    public Long getCurrentUsage() {
        return currentUsage;
    }

    public Long getLimit() {
        return limit;
    }
}

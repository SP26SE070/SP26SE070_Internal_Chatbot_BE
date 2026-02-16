package com.gsp26se114.chatbot_rag_be.entity;

/**
 * Subscription tier/plan for tenant organizations
 */
public enum SubscriptionTier {
    TRIAL,        // Free trial (14-30 days)
    STARTER,      // Basic plan (small teams)
    STANDARD,     // Standard plan (medium teams)
    ENTERPRISE    // Enterprise plan (large organizations)
}

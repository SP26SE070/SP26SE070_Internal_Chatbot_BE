package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {
    /**
     * Monthly Recurring Revenue (USD)
     */
    private BigDecimal monthlyRecurringRevenue;
    
    /**
     * Churn Rate (Percentage of tenants who left in the last month)
     */
    private Double churnRate;
    
    /**
     * Total Token Consumption across all tenants
     */
    private Long totalTokenConsumption;
    
    /**
     * Total number of active tenants
     */
    private Long activeTenants;
    
    /**
     * Total number of pending tenants
     */
    private Long pendingTenants;
    
    /**
     * Total number of rejected tenants
     */
    private Long rejectedTenants;
}

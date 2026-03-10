package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO cho Tenant Admin Dashboard Analytics
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TenantAnalyticsResponse {
    
    // ========== USER STATISTICS ==========
    private Integer totalUsers;
    private Integer activeUsers; // Users logged in within last 30 days
    private Integer newUsersThisMonth;
    
    // ========== USERS BY ROLE ==========
    private Map<String, Long> usersByRole; // {"CONTENT_MANAGER": 5, "EMPLOYEE": 20}
    
    // ========== USERS BY DEPARTMENT ==========
    private Map<String, Long> usersByDepartment; // {"IT": 10, "HR": 5, "SALES": 8}
    
    // ========== RECENT ACTIVITY ==========
    private Integer usersCreatedLast7Days;
    private Integer usersCreatedLast30Days;
}

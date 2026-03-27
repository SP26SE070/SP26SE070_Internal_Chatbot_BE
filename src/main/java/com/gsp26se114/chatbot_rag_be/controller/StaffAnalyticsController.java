package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/staff/analytics")
@RequiredArgsConstructor
@Tag(name = "09. 📊 Staff - Tenant Analytics", description = "Dashboard thống kê platform (cùng shape với admin/analytics/dashboard, trừ semantics totalUsers)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('STAFF')")
public class StaffAnalyticsController {

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final HealthEndpoint healthEndpoint;

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard cho STAFF",
               description = "Schema khớp admin ở system + tenants. "
                   + "system: status (STABLE|DEGRADED), statusLabel, appUptimeSeconds, appStartedAt, checkedAt. "
                   + "tenants: total = số tổ chức đã phê duyệt (ACTIVE + SUSPENDED); pending/rejected không cộng vào total. "
                   + "active, pending, suspended, rejected; activePercentage = active / total (đã duyệt). "
                   + "totalUsers = tenants.active (số tổ chức ACTIVE; không phải user platform). "
                   + "documents + llmUsage cùng cấu trúc với admin dashboard.")
    public ResponseEntity<Map<String, Object>> getStaffDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("system", buildSystemStats());
        
        // Tenant statistics
        long activeTenants = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.ACTIVE);
        long pendingTenants = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.PENDING);
        long suspendedTenants = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.SUSPENDED);
        long rejectedTenants = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.REJECTED);
        long approvedTenants = activeTenants + suspendedTenants;

        Map<String, Object> tenantStats = new HashMap<>();
        tenantStats.put("total", approvedTenants);
        tenantStats.put("active", activeTenants);
        tenantStats.put("pending", pendingTenants);
        tenantStats.put("suspended", suspendedTenants);
        tenantStats.put("rejected", rejectedTenants);
        tenantStats.put("activePercentage", approvedTenants > 0 ? (activeTenants * 100.0 / approvedTenants) : 0.0);
        dashboard.put("tenants", tenantStats);

        dashboard.put("totalUsers", activeTenants);
        
        // Subscription statistics
        long totalSubscriptions = subscriptionRepository.count();
        Map<String, Long> subscriptionStats = new HashMap<>();
        subscriptionStats.put("total", totalSubscriptions);
        dashboard.put("subscriptions", subscriptionStats);
        
        long totalDocuments = documentRepository.count();
        long totalChunks = documentChunkRepository.count();
        Map<String, Long> documentStats = new HashMap<>();
        documentStats.put("totalDocuments", totalDocuments);
        documentStats.put("totalChunks", totalChunks);
        documentStats.put("averageChunksPerDocument", totalDocuments > 0 ? (totalChunks / totalDocuments) : 0);
        dashboard.put("documents", documentStats);
        dashboard.put("totalDocuments", totalDocuments);

        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long totalTokens = chatMessageRepository.sumAllTokens();
        long totalRequests = chatMessageRepository.countAllRequests();
        long tokensMonth = chatMessageRepository.sumAllTokensSince(startOfMonth);
        long requestsMonth = chatMessageRepository.countAllRequestsSince(startOfMonth);
        Map<String, Object> llmStats = new HashMap<>();
        llmStats.put("totalTokensUsed", totalTokens);
        llmStats.put("totalRequests", totalRequests);
        llmStats.put("tokensThisMonth", tokensMonth);
        llmStats.put("requestsThisMonth", requestsMonth);
        llmStats.put("averageTokensPerRequest", totalRequests > 0 ? totalTokens / totalRequests : 0);
        dashboard.put("llmUsage", llmStats);

        return ResponseEntity.ok(dashboard);
    }

    private Map<String, Object> buildSystemStats() {
        Map<String, Object> system = new HashMap<>();
        HealthComponent health = healthEndpoint.health();
        boolean up = health != null && "UP".equalsIgnoreCase(health.getStatus().getCode());
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = runtime.getUptime();
        long startedAtMs = runtime.getStartTime();

        system.put("status", up ? "STABLE" : "DEGRADED");
        system.put("statusLabel", up ? "Ổn định" : "Không ổn định");
        system.put("appUptimeSeconds", uptimeMs / 1000);
        system.put("appStartedAt", Instant.ofEpochMilli(startedAtMs).toString());
        system.put("checkedAt", LocalDateTime.now());
        return system;
    }

    @GetMapping("/tenants")
    @Operation(summary = "Thống kê chi tiết tenants",
               description = "total = ACTIVE + SUSPENDED (đã phê duyệt); rejected/pending không vào total.")
    public ResponseEntity<Map<String, Object>> getTenantStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long active = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.ACTIVE);
        long pending = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.PENDING);
        long suspended = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.SUSPENDED);
        long rejected = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.REJECTED);
        long approved = active + suspended;

        stats.put("total", approved);
        stats.put("active", active);
        stats.put("pending", pending);
        stats.put("suspended", suspended);
        stats.put("rejected", rejected);
        stats.put("activePercentage", approved > 0 ? (active * 100.0 / approved) : 0);
        stats.put("pendingForApproval", pending);
        
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "Thống kê subscriptions của các tenants", 
               description = "Tổng quan về subscriptions")
    public ResponseEntity<Map<String, Object>> getSubscriptionStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long total = subscriptionRepository.count();
        stats.put("total", total);
        
        return ResponseEntity.ok(stats);
    }
}

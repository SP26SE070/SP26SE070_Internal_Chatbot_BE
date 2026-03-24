package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.RoleType;
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
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "07. 📊 Super Admin - System Analytics", 
     description = "Thống kê toàn hệ thống: subscription, plan, document & RAG, token/LLM usage (SUPER_ADMIN)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAnalyticsController {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final HealthEndpoint healthEndpoint;

    @GetMapping("/dashboard")
    @Operation(summary = "Lấy thống kê dashboard",
               description = "system + tenants (total, active, pending, suspended, activePercentage) cùng shape với staff dashboard. "
                   + "totalUsers = tài khoản platform (role SYSTEM: SUPER_ADMIN, STAFF) đang active.")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("system", buildSystemStats());
        
        // Tenant statistics
        long totalTenants = tenantRepository.count();
        long activeTenants = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.ACTIVE);
        long pendingTenants = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.PENDING);
        long suspendedTenants = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.SUSPENDED);
        
        Map<String, Object> tenantStats = new HashMap<>();
        tenantStats.put("total", totalTenants);
        tenantStats.put("active", activeTenants);
        tenantStats.put("pending", pendingTenants);
        tenantStats.put("suspended", suspendedTenants);
        tenantStats.put("activePercentage", totalTenants > 0 ? (activeTenants * 100.0 / totalTenants) : 0.0);
        stats.put("tenants", tenantStats);
        
        long platformUsers = userRepository.countActiveUsersWithRoleType(RoleType.SYSTEM);
        stats.put("totalUsers", platformUsers);
        
        // Subscription statistics
        long totalSubscriptions = subscriptionRepository.count();
        
        Map<String, Long> subscriptionStats = new HashMap<>();
        subscriptionStats.put("total", totalSubscriptions);
        stats.put("subscriptions", subscriptionStats);
        
        // Document statistics
        long totalDocuments = documentRepository.count();
        long totalChunks = documentChunkRepository.count();
        
        Map<String, Long> documentStats = new HashMap<>();
        documentStats.put("totalDocuments", totalDocuments);
        documentStats.put("totalChunks", totalChunks);
        documentStats.put("averageChunksPerDocument", totalDocuments > 0 ? (totalChunks / totalDocuments) : 0);
        stats.put("documents", documentStats);
        
        // LLM usage từ chat_messages
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long totalTokens   = chatMessageRepository.sumAllTokens();
        long totalRequests = chatMessageRepository.countAllRequests();
        long tokensMonth   = chatMessageRepository.sumAllTokensSince(startOfMonth);
        long requestsMonth = chatMessageRepository.countAllRequestsSince(startOfMonth);

        Map<String, Object> llmStats = new HashMap<>();
        llmStats.put("totalTokensUsed", totalTokens);
        llmStats.put("totalRequests", totalRequests);
        llmStats.put("tokensThisMonth", tokensMonth);
        llmStats.put("requestsThisMonth", requestsMonth);
        llmStats.put("averageTokensPerRequest", totalRequests > 0 ? totalTokens / totalRequests : 0);
        stats.put("llmUsage", llmStats);
        
        return ResponseEntity.ok(stats);
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

    @GetMapping("/llm-usage")
    @Operation(summary = "Thống kê LLM usage toàn hệ thống", 
               description = "Tổng token và request của tất cả tenants")
    public ResponseEntity<Map<String, Object>> getLLMUsageStats() {
        Map<String, Object> stats = new HashMap<>();
        
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startOfToday  = LocalDateTime.now().toLocalDate().atStartOfDay();

        long totalTokens   = chatMessageRepository.sumAllTokens();
        long totalRequests = chatMessageRepository.countAllRequests();
        long tokensMonth   = chatMessageRepository.sumAllTokensSince(startOfMonth);
        long requestsMonth = chatMessageRepository.countAllRequestsSince(startOfMonth);
        long tokensToday   = chatMessageRepository.sumAllTokensSince(startOfToday);
        long requestsToday = chatMessageRepository.countAllRequestsSince(startOfToday);

        stats.put("totalTokensAllTime", totalTokens);
        stats.put("totalRequestsAllTime", totalRequests);
        stats.put("tokensThisMonth", tokensMonth);
        stats.put("requestsThisMonth", requestsMonth);
        stats.put("tokensToday", tokensToday);
        stats.put("requestsToday", requestsToday);
        stats.put("averageTokensPerRequest", totalRequests > 0 ? totalTokens / totalRequests : 0);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/tenants")
    @Operation(summary = "Thống kê tenants", description = "Chi tiết thống kê về tenants")
    public ResponseEntity<Map<String, Object>> getTenantAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        
        long total = tenantRepository.count();
        long active = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.ACTIVE);
        long pending = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.PENDING);
        long suspended = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.SUSPENDED);
        
        analytics.put("total", total);
        analytics.put("active", active);
        analytics.put("pending", pending);
        analytics.put("suspended", suspended);
        analytics.put("activePercentage", total > 0 ? (active * 100.0 / total) : 0);
        
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "Thống kê subscriptions", description = "Chi tiết thống kê về subscriptions")
    public ResponseEntity<Map<String, Object>> getSubscriptionAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        
        long total = subscriptionRepository.count();
        
        analytics.put("total", total);
        
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/documents")
    @Operation(summary = "Thống kê documents", description = "Chi tiết thống kê về tài liệu và RAG")
    public ResponseEntity<Map<String, Object>> getDocumentAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        
        long totalDocuments = documentRepository.count();
        long totalChunks = documentChunkRepository.count();
        
        analytics.put("totalDocuments", totalDocuments);
        analytics.put("totalChunks", totalChunks);
        analytics.put("averageChunksPerDocument", totalDocuments > 0 ? (totalChunks * 1.0 / totalDocuments) : 0);
        
        return ResponseEntity.ok(analytics);
    }
}

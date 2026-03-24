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
@Tag(name = "09. 📊 Staff - Tenant Analytics", description = "Thống kê về tenants, subscriptions (STAFF)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('STAFF')")
public class StaffAnalyticsController {

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DocumentRepository documentRepository;
    private final HealthEndpoint healthEndpoint;

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard cho STAFF",
               description = "Schema khớp admin ở system + tenants. "
                   + "system: status (STABLE|DEGRADED), statusLabel, appUptimeSeconds, appStartedAt, checkedAt. "
                   + "tenants: total, active, pending, suspended, activePercentage (tuỳ chọn hiển thị). "
                   + "totalUsers luôn bằng tenants.active (số tổ chức ACTIVE; không phải tổng user toàn hệ thống). "
                   + "subscriptions: { total }, totalDocuments.")
    public ResponseEntity<Map<String, Object>> getStaffDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("system", buildSystemStats());
        
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
        dashboard.put("tenants", tenantStats);

        dashboard.put("totalUsers", activeTenants);
        
        // Subscription statistics
        long totalSubscriptions = subscriptionRepository.count();
        Map<String, Long> subscriptionStats = new HashMap<>();
        subscriptionStats.put("total", totalSubscriptions);
        dashboard.put("subscriptions", subscriptionStats);
        
        // Document statistics
        long totalDocuments = documentRepository.count();
        dashboard.put("totalDocuments", totalDocuments);
        
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
               description = "Chi tiết về các tenant trong hệ thống")
    public ResponseEntity<Map<String, Object>> getTenantStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long total = tenantRepository.count();
        long active = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.ACTIVE);
        long pending = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.PENDING);
        long suspended = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.SUSPENDED);
        
        stats.put("total", total);
        stats.put("active", active);
        stats.put("pending", pending);
        stats.put("suspended", suspended);
        stats.put("activePercentage", total > 0 ? (active * 100.0 / total) : 0);
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

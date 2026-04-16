package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.RoleType;
import com.gsp26se114.chatbot_rag_be.entity.AuditLog;
import com.gsp26se114.chatbot_rag_be.entity.PaymentTransaction;
import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final HealthEndpoint healthEndpoint;

    @GetMapping("/dashboard")
    @Operation(summary = "Lấy thống kê dashboard",
               description = "system + tenants: total = tổ chức đã phê duyệt (ACTIVE + SUSPENDED); pending/rejected không vào total; "
                   + "active, pending, suspended, rejected; activePercentage = active / total (đã duyệt). "
                   + "totalUsers = tài khoản platform (role SYSTEM: SUPER_ADMIN, STAFF) đang active.")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("system", buildSystemStats());
        
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

    @GetMapping("/revenue")
    @Operation(summary = "Revenue time-series cho Super Admin",
               description = "Mặc định bucket=month: mỗi phần tử series là một tháng theo timezone (period dạng yyyy-MM).")
    public ResponseEntity<Map<String, Object>> getRevenueSeries(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "month") String bucket,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam(defaultValue = "USD") String currency
    ) {
        BucketType bucketType = BucketType.from(bucket);
        ZoneId zoneId = parseZoneId(timezone);
        TimeRange range = resolveTimeRange(from, to, zoneId);

        List<PaymentTransaction> successfulPayments = paymentTransactionRepository.findSuccessfulInRange(
                range.fromUtc().toLocalDateTime(),
                range.toUtc().toLocalDateTime()
        );

        Map<String, RevenuePoint> points = initRevenuePoints(range, bucketType, zoneId);
        for (PaymentTransaction tx : successfulPayments) {
            ZonedDateTime occurredAt = toZonedDateTime(tx, zoneId);
            String period = formatPeriod(occurredAt, bucketType);
            RevenuePoint point = points.get(period);
            if (point == null) {
                continue;
            }
            BigDecimal amount = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
            point.revenue = point.revenue.add(amount);
            point.completedPayments += 1;
        }

        List<Map<String, Object>> series = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        String peakPeriod = null;
        BigDecimal peakRevenue = BigDecimal.ZERO;

        for (RevenuePoint p : points.values()) {
            Map<String, Object> row = new HashMap<>();
            row.put("period", p.period);
            row.put("label", p.label);
            if (bucketType == BucketType.MONTH) {
                row.put("month", p.period);
            }
            row.put("revenue", p.revenue);
            row.put("completedPayments", p.completedPayments);
            series.add(row);

            totalRevenue = totalRevenue.add(p.revenue);
            if (p.revenue.compareTo(peakRevenue) > 0) {
                peakRevenue = p.revenue;
                peakPeriod = p.period;
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalRevenue", totalRevenue);
        summary.put("averagePerBucket", series.isEmpty()
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(series.size()), 2, java.math.RoundingMode.HALF_UP));
        summary.put("peakPeriod", peakPeriod);
        summary.put("peakRevenue", peakRevenue);

        Map<String, Object> response = new HashMap<>();
        response.put("currency", currency);
        response.put("bucket", bucketType.value);
        response.put("granularity", bucketType.value);
        response.put("from", range.fromUtc().toInstant().toString());
        response.put("to", range.toUtc().toInstant().toString());
        response.put("series", series);
        response.put("summary", summary);
        response.put("notes", "Revenue includes only SUCCESS payments. Refund/chargeback are currently excluded.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recent-activities")
    @Operation(summary = "Recent activity feed cho Super Admin",
               description = "Query param `types` là danh sách loại đã chuẩn hoá (vd: TENANT_CREATED, DOCUMENT_UPLOADED), "
                   + "khớp với mapType() — không cần trùng raw action DB nếu khác quy ước.")
    public ResponseEntity<Map<String, Object>> getRecentActivities(
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String severities
    ) {
        int safeLimit = (limit == null) ? 20 : Math.min(Math.max(limit, 1), 100);
        // Always pass non-null beforeTime to avoid PostgreSQL null-typed parameter issue.
        LocalDateTime beforeTime = parseCursorToUtcLocalDateTime(cursor);
        if (beforeTime == null) {
            beforeTime = LocalDateTime.now(ZoneOffset.UTC).plusYears(100);
        }
        Set<String> typeFilter = new HashSet<>(normalizeActivityTypes(parseCsv(types)));
        Set<String> severityFilter = new HashSet<>(parseCsvLower(severities));

        // Lấy theo thời gian rồi lọc theo mapType/severity trong memory — tránh lệch với WHERE action IN (:types).
        int fetchSize = Math.min(Math.max(safeLimit * (typeFilter.isEmpty() ? 4 : 25), 80), 500);
        List<AuditLog> logs = auditLogRepository.findRecentForDashboard(beforeTime, PageRequest.of(0, fetchSize));

        List<Map<String, Object>> items = new ArrayList<>();
        for (AuditLog log : logs) {
            String mappedType = mapType(log);
            String severity = mapSeverity(log);
            if (!typeFilter.isEmpty() && !typeFilter.contains(mappedType)) {
                continue;
            }
            if (!severityFilter.isEmpty() && !severityFilter.contains(severity.toLowerCase())) {
                continue;
            }
            items.add(mapActivityItem(log, mappedType, severity));
            if (items.size() == safeLimit + 1) {
                break;
            }
        }

        String nextCursor = null;
        if (items.size() > safeLimit) {
            Map<String, Object> last = items.remove(items.size() - 1);
            nextCursor = (String) last.get("occurredAt");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("nextCursor", nextCursor);
        return ResponseEntity.ok(response);
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

    private record TimeRange(OffsetDateTime fromUtc, OffsetDateTime toUtc) {}

    private static class RevenuePoint {
        String period;
        String label;
        BigDecimal revenue = BigDecimal.ZERO;
        int completedPayments = 0;
    }

    private enum BucketType {
        DAY("day"), WEEK("week"), MONTH("month");
        final String value;
        BucketType(String value) { this.value = value; }
        static BucketType from(String value) {
            if (value == null) {
                return MONTH;
            }
            return switch (value.toLowerCase()) {
                case "day" -> DAY;
                case "week" -> WEEK;
                case "month" -> MONTH;
                default -> throw new IllegalArgumentException("bucket phải là day|week|month");
            };
        }
    }

    private ZoneId parseZoneId(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            throw new IllegalArgumentException("timezone không hợp lệ: " + tz);
        }
    }

    private TimeRange resolveTimeRange(String from, String to, ZoneId zoneId) {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime fromZdt;
        ZonedDateTime toZdt;

        if (from == null || from.isBlank()) {
            fromZdt = now.minusMonths(5).withDayOfMonth(1).toLocalDate().atStartOfDay(zoneId);
        } else {
            fromZdt = parseDateTimeFlexible(from, zoneId);
        }
        if (to == null || to.isBlank()) {
            toZdt = now.withHour(23).withMinute(59).withSecond(59).withNano(0);
        } else {
            toZdt = parseDateTimeFlexible(to, zoneId);
        }
        if (toZdt.isBefore(fromZdt)) {
            throw new IllegalArgumentException("to phải lớn hơn hoặc bằng from");
        }
        return new TimeRange(fromZdt.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime(),
                toZdt.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime());
    }

    private ZonedDateTime parseDateTimeFlexible(String raw, ZoneId zoneId) {
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(zoneId);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value).atZone(zoneId);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(value).atStartOfDay(zoneId);
        } catch (Exception ignored) {
        }
        throw new IllegalArgumentException("Datetime không hợp lệ: " + raw);
    }

    private Map<String, RevenuePoint> initRevenuePoints(TimeRange range, BucketType bucketType, ZoneId zoneId) {
        Map<String, RevenuePoint> points = new LinkedHashMap<>();
        ZonedDateTime start = range.fromUtc().atZoneSameInstant(zoneId);
        ZonedDateTime end = range.toUtc().atZoneSameInstant(zoneId);
        ZonedDateTime cursor = truncateToBucketStart(start, bucketType);

        while (!cursor.isAfter(end)) {
            String period = formatPeriod(cursor, bucketType);
            RevenuePoint point = new RevenuePoint();
            point.period = period;
            point.label = bucketType == BucketType.MONTH
                    ? String.format("Tháng %d/%d", cursor.getMonthValue(), cursor.getYear())
                    : period;
            points.put(period, point);
            cursor = incrementBucket(cursor, bucketType);
        }
        return points;
    }

    private ZonedDateTime truncateToBucketStart(ZonedDateTime dt, BucketType bucket) {
        return switch (bucket) {
            case DAY -> dt.toLocalDate().atStartOfDay(dt.getZone());
            case WEEK -> dt.with(java.time.DayOfWeek.MONDAY).toLocalDate().atStartOfDay(dt.getZone());
            case MONTH -> dt.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay(dt.getZone());
        };
    }

    private ZonedDateTime incrementBucket(ZonedDateTime dt, BucketType bucket) {
        return switch (bucket) {
            case DAY -> dt.plusDays(1);
            case WEEK -> dt.plusWeeks(1);
            case MONTH -> dt.plusMonths(1);
        };
    }

    private String formatPeriod(ZonedDateTime dt, BucketType bucketType) {
        return switch (bucketType) {
            case DAY -> dt.toLocalDate().toString();
            case WEEK -> dt.with(java.time.DayOfWeek.MONDAY).toLocalDate().toString();
            case MONTH -> String.format("%04d-%02d", dt.getYear(), dt.getMonthValue());
        };
    }

    private ZonedDateTime toZonedDateTime(PaymentTransaction tx, ZoneId zoneId) {
        LocalDateTime time = tx.getPaidAt() != null ? tx.getPaidAt() : tx.getCreatedAt();
        return time.atZone(ZoneOffset.UTC).withZoneSameInstant(zoneId);
    }

    private LocalDateTime parseCursorToUtcLocalDateTime(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(cursor).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception e) {
            throw new IllegalArgumentException("cursor không hợp lệ, cần ISO datetime");
        }
    }

    private List<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<String> parseCsvLower(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .toList();
    }

    /** Chuẩn hoá giống mapType(): UPPER_SNAKE (FE có thể gửi tenant-created). */
    private List<String> normalizeActivityTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            out.add(t.toUpperCase().replace('-', '_'));
        }
        return out;
    }

    private String mapType(AuditLog log) {
        String action = log.getAction() == null ? "" : log.getAction().toUpperCase();
        if (action.contains("TENANT") && action.contains("CREATE")) return "TENANT_CREATED";
        if (action.contains("TENANT") && action.contains("UPDATE")) return "TENANT_UPDATED";
        if (action.contains("TENANT") && (action.contains("APPROVE") || action.contains("REJECT") || action.contains("SUSPEND"))) return "TENANT_STATUS_CHANGED";
        if (action.contains("SUBSCRIPTION") && action.contains("CREATE")) return "SUBSCRIPTION_CREATED";
        if (action.contains("SUBSCRIPTION") && action.contains("RENEW")) return "SUBSCRIPTION_RENEWED";
        if (action.contains("SUBSCRIPTION") && action.contains("CANCEL")) return "SUBSCRIPTION_CANCELLED";
        if (action.contains("ROLE")) return "ROLE_CHANGED";
        if (action.contains("DOCUMENT") && action.contains("UPLOAD")) return "DOCUMENT_UPLOADED";
        if (action.contains("PAYMENT") && action.contains("SUCCESS")) return "PAYMENT_COMPLETED";
        if (action.contains("PAYMENT") && (action.contains("FAIL") || action.contains("ERROR"))) return "PAYMENT_FAILED";
        if (action.contains("WARNING")) return "SYSTEM_WARNING";
        if (action.contains("ERROR")) return "SYSTEM_ERROR";
        if (action.contains("USER") && action.contains("CREATE")) return "USER_REGISTERED";
        return "SECURITY_EVENT";
    }

    private String mapSeverity(AuditLog log) {
        String status = log.getStatus() == null ? "" : log.getStatus().toUpperCase();
        String action = log.getAction() == null ? "" : log.getAction().toUpperCase();
        if ("FAILED".equals(status) || action.contains("ERROR") || action.contains("FAIL")) return "error";
        if (action.contains("WARNING") || action.contains("SUSPEND")) return "warning";
        return "info";
    }

    private Map<String, Object> mapActivityItem(AuditLog log, String type, String severity) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", "evt_" + log.getId());
        item.put("type", type);
        item.put("severity", severity);
        item.put("message", buildMessage(log, type));
        item.put("occurredAt", log.getCreatedAt().atOffset(ZoneOffset.UTC).toInstant().toString());

        Map<String, Object> actor = null;
        if (log.getUserId() != null || log.getUserEmail() != null) {
            actor = new HashMap<>();
            actor.put("id", log.getUserId() != null ? log.getUserId().toString() : null);
            actor.put("name", log.getUserEmail());
            actor.put("role", log.getUserRole());
        }
        item.put("actor", actor);

        Map<String, Object> target = new HashMap<>();
        target.put("kind", log.getEntityType() != null ? log.getEntityType().toLowerCase() : "system");
        target.put("id", log.getEntityId());
        target.put("name", log.getEntityType() != null ? log.getEntityType() : "System");
        item.put("target", target);

        item.put("metadata", log.getNewValue() != null ? log.getNewValue() : Map.of());
        return item;
    }

    private String buildMessage(AuditLog log, String type) {
        if (log.getDescription() != null && !log.getDescription().isBlank()) {
            return log.getDescription();
        }
        return switch (type) {
            case "TENANT_CREATED" -> "A new tenant has been added to the platform";
            case "TENANT_UPDATED" -> "A tenant profile has been updated";
            case "TENANT_STATUS_CHANGED" -> "A tenant status has changed";
            case "SUBSCRIPTION_CREATED" -> "A new subscription was created";
            case "SUBSCRIPTION_RENEWED" -> "A subscription was renewed";
            case "SUBSCRIPTION_CANCELLED" -> "A subscription was cancelled";
            case "PAYMENT_COMPLETED" -> "A payment was completed successfully";
            case "PAYMENT_FAILED" -> "A payment attempt failed";
            case "SYSTEM_WARNING" -> "System warning detected";
            case "SYSTEM_ERROR" -> "System error detected";
            default -> (log.getAction() != null ? log.getAction() : "System activity");
        };
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
    @Operation(summary = "Thống kê tenants",
               description = "total = ACTIVE + SUSPENDED (đã phê duyệt); rejected/pending không vào total.")
    public ResponseEntity<Map<String, Object>> getTenantAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        
        long active = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.ACTIVE);
        long pending = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.PENDING);
        long suspended = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.SUSPENDED);
        long rejected = tenantRepository.countByStatus(com.gsp26se114.chatbot_rag_be.entity.TenantStatus.REJECTED);
        long approved = active + suspended;

        analytics.put("total", approved);
        analytics.put("active", active);
        analytics.put("pending", pending);
        analytics.put("suspended", suspended);
        analytics.put("rejected", rejected);
        analytics.put("activePercentage", approved > 0 ? (active * 100.0 / approved) : 0);
        
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

    @GetMapping("/ai-overview")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "System-wide AI feedback overview", description = "Tổng quan phản hồi AI toàn hệ thống")
    public ResponseEntity<Map<String, Object>> getAiOverview() {
        Long totalMessages = chatMessageRepository.countAllRequests();
        Long ratedMessages = chatMessageRepository.countAllRatedMessages();
        Long positiveRatings = chatMessageRepository.countAllPositiveRatings();
        Long negativeRatings = chatMessageRepository.countAllNegativeRatings();

        double helpfulPercent = ratedMessages > 0
                ? Math.round((positiveRatings * 100.0 / ratedMessages) * 10.0) / 10.0 : 0.0;

        Map<String, Object> response = new HashMap<>();
        response.put("totalMessages", totalMessages);
        response.put("ratedMessages", ratedMessages);
        response.put("positiveRatings", positiveRatings);
        response.put("negativeRatings", negativeRatings);
        response.put("helpfulPercent", helpfulPercent);
        response.put("notHelpfulPercent", ratedMessages > 0 ? 100.0 - helpfulPercent : 0.0);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/tenant-performance")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Per-tenant AI feedback performance", description = "Hiệu suất phản hồi AI theo từng tenant")
    public ResponseEntity<List<Map<String, Object>>> getTenantPerformance() {
        List<UUID> tenantIds = chatMessageRepository.findDistinctTenantIds();

        List<Map<String, Object>> result = tenantIds.stream().map(tenantId -> {
            Long total = chatMessageRepository.countRequestsByTenantId(tenantId);
            Long rated = chatMessageRepository.countRatedMessagesByTenant(tenantId);
            Long positive = chatMessageRepository.countPositiveRatingsByTenant(tenantId);
            Long negative = chatMessageRepository.countNegativeRatingsByTenant(tenantId);

            double helpfulPercent = rated > 0
                    ? Math.round((positive * 100.0 / rated) * 10.0) / 10.0 : 0.0;

            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            String tenantName = tenant != null ? tenant.getName() : tenantId.toString();

            Map<String, Object> item = new HashMap<>();
            item.put("tenantId", tenantId);
            item.put("tenantName", tenantName);
            item.put("totalMessages", total);
            item.put("ratedMessages", rated);
            item.put("helpfulPercent", helpfulPercent);
            item.put("lowPerforming", helpfulPercent < 70.0 && rated > 0);
            return item;
        }).sorted((a, b) -> Double.compare(
                (Double) a.get("helpfulPercent"),
                (Double) b.get("helpfulPercent")
        )).toList();

        return ResponseEntity.ok(result);
    }
}

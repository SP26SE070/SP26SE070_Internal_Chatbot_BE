package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.entity.SubscriptionStatus;
import com.gsp26se114.chatbot_rag_be.entity.SubscriptionTier;
import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionPlanRepository;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/subscriptions")
@RequiredArgsConstructor
@Tag(name = "11. 🧾 Staff - Subscription Management", description = "Danh sách subscriptions để STAFF quản lý")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('STAFF')")
public class StaffSubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @GetMapping
    @Operation(summary = "Danh sách subscriptions cho Staff Manage Subscriptions",
            description = "Trả về records subscription (không phải tenant approval list), dạng { items, nextCursor }.")
    public ResponseEntity<Map<String, Object>> listSubscriptions(
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        int safeLimit = (limit == null) ? 20 : Math.min(Math.max(limit, 1), 100);
        LocalDateTime beforeTime = parseCursorToUtcLocalDateTime(cursor);
        if (beforeTime == null) {
            beforeTime = LocalDateTime.now(ZoneOffset.UTC).plusYears(100);
        }

        // Fetch extra item to determine next cursor.
        List<Subscription> all = subscriptionRepository.findRecentForStaff(beforeTime, PageRequest.of(0, safeLimit + 1));

        List<Subscription> page = all.size() > safeLimit ? all.subList(0, safeLimit) : all;
        Set<UUID> tenantIds = new HashSet<>();
        Set<UUID> planIds = new HashSet<>();
        for (Subscription s : page) {
            if (s.getTenantId() != null) tenantIds.add(s.getTenantId());
            if (s.getPlanId() != null) planIds.add(s.getPlanId());
        }

        Map<UUID, Tenant> tenantMap = new HashMap<>();
        if (!tenantIds.isEmpty()) {
            for (Tenant t : tenantRepository.findAllById(tenantIds)) {
                tenantMap.put(t.getId(), t);
            }
        }

        Map<UUID, String> planNameMap = new HashMap<>();
        if (!planIds.isEmpty()) {
            subscriptionPlanRepository.findAllById(planIds).forEach(plan -> planNameMap.put(plan.getId(), plan.getName()));
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Subscription s : page) {
            Tenant tenant = s.getTenantId() != null ? tenantMap.get(s.getTenantId()) : null;

            Map<String, Object> row = new HashMap<>();
            row.put("id", s.getId());
            row.put("tenantId", s.getTenantId());
            row.put("tenantName", tenant != null ? tenant.getName() : null);
            row.put("tenantEmail", tenant != null ? tenant.getContactEmail() : null);
            row.put("tier", s.getTier());
            row.put("planName", s.getPlanId() != null ? planNameMap.get(s.getPlanId()) : null);
            row.put("status", mapStatusForStaff(s));
            row.put("subscriptionCode", s.getTransactionCode());
            row.put("startedAt", s.getStartDate());
            row.put("endedAt", s.getEndDate());
            row.put("autoRenew", s.getAutoRenew());
            items.add(row);
        }

        String nextCursor = null;
        if (all.size() > safeLimit) {
            Subscription last = page.get(page.size() - 1);
            nextCursor = last.getCreatedAt().atOffset(ZoneOffset.UTC).toInstant().toString();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("nextCursor", nextCursor);
        return ResponseEntity.ok(response);
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

    private String mapStatusForStaff(Subscription s) {
        if (Boolean.TRUE.equals(s.getIsTrial()) || SubscriptionTier.TRIAL.equals(s.getTier())) {
            return "TRIAL";
        }
        if (SubscriptionStatus.ACTIVE.equals(s.getStatus())) {
            return "ACTIVE";
        }
        if (SubscriptionStatus.EXPIRED.equals(s.getStatus())) {
            return "EXPIRED";
        }
        return "CANCELLED";
    }
}

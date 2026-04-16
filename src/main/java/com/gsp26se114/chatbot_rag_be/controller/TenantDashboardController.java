package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.entity.ChatMessage;
import com.gsp26se114.chatbot_rag_be.repository.*;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant-admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "15. 📊 Tenant Admin - Dashboard & Analytics", 
    description = "Dashboard và thống kê của tenant (token, LLM request, documents) - TENANT_ADMIN hoặc custom role có quyền phù hợp")
@SecurityRequirement(name = "bearerAuth")
public class TenantDashboardController {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TenantRepository tenantRepository;

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('ANALYTICS_VIEW')")
    @Operation(summary = "Dashboard tổng quan của tenant", 
               description = "Xem thống kê tổng quan: users, documents, chunks của tenant")
    public ResponseEntity<Map<String, Object>> getTenantDashboard(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> dashboard = new HashMap<>();
        
        // User statistics in this tenant
        long totalUsers = userRepository.countByTenantId(userPrincipal.getTenantId());
        dashboard.put("totalUsers", totalUsers);
        
        // Document statistics
        long totalDocuments = documentRepository.countByTenantId(userPrincipal.getTenantId());
        long totalChunks = documentChunkRepository.countByTenantId(userPrincipal.getTenantId());
        
        Map<String, Long> documentStats = new HashMap<>();
        documentStats.put("totalDocuments", totalDocuments);
        documentStats.put("totalChunks", totalChunks);
        documentStats.put("averageChunksPerDocument", totalDocuments > 0 ? (totalChunks / totalDocuments) : 0);
        dashboard.put("documents", documentStats);
        
        // LLM usage từ chat_messages
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        UUID tenantId = userPrincipal.getTenantId();
        long totalTokens   = chatMessageRepository.sumTokensByTenantId(tenantId);
        long totalRequests = chatMessageRepository.countRequestsByTenantId(tenantId);
        long tokensMonth   = chatMessageRepository.sumTokensByTenantIdSince(tenantId, startOfMonth);
        long requestsMonth = chatMessageRepository.countRequestsByTenantIdSince(tenantId, startOfMonth);

        Map<String, Object> llmStats = new HashMap<>();
        llmStats.put("totalTokensUsed", totalTokens);
        llmStats.put("totalRequests", totalRequests);
        llmStats.put("tokensThisMonth", tokensMonth);
        llmStats.put("requestsThisMonth", requestsMonth);
        llmStats.put("averageTokensPerRequest", totalRequests > 0 ? totalTokens / totalRequests : 0);
        dashboard.put("llmUsage", llmStats);
        
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/tenant")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Xem thông tin tenant", description = "TENANT_ADMIN xem thông tin tổ chức (tenant) của mình")
    public ResponseEntity<Map<String, Object>> getTenantInfo(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        Tenant tenant = tenantRepository.findById(userPrincipal.getTenantId())
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + userPrincipal.getTenantId()));

        Map<String, Object> data = new HashMap<>();
        data.put("tenant_id", tenant.getId());
        data.put("name", tenant.getName());
        data.put("address", tenant.getAddress());
        data.put("website", tenant.getWebsite());
        data.put("company_size", tenant.getCompanySize());
        data.put("contact_email", tenant.getContactEmail());
        data.put("representative_name", tenant.getRepresentativeName());
        data.put("representative_position", tenant.getRepresentativePosition());
        data.put("representative_phone", tenant.getRepresentativePhone());
        data.put("request_message", tenant.getRequestMessage());
        data.put("requested_at", tenant.getRequestedAt());
        data.put("status", tenant.getStatus() != null ? tenant.getStatus().name() : null);
        data.put("reviewed_by", tenant.getReviewedBy());
        data.put("reviewed_at", tenant.getReviewedAt());
        data.put("rejection_reason", tenant.getRejectionReason());
        data.put("subscription_id", tenant.getSubscriptionId());
        data.put("is_trial", tenant.getIsTrial());
        data.put("trial_used", tenant.getTrialUsed());
        data.put("created_at", tenant.getCreatedAt());
        data.put("updated_at", tenant.getUpdatedAt());
        return ResponseEntity.ok(data);
    }

    @GetMapping("/documents")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('ANALYTICS_VIEW') or hasAuthority('DOCUMENT_READ')")
    @Operation(summary = "Thống kê documents của tenant", 
               description = "Chi tiết về tài liệu và Document Dashboard")
    public ResponseEntity<Map<String, Object>> getDocumentStatistics(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> stats = new HashMap<>();
        
        long totalDocuments = documentRepository.countByTenantId(userPrincipal.getTenantId());
        long totalChunks = documentChunkRepository.countByTenantId(userPrincipal.getTenantId());
        
        stats.put("totalDocuments", totalDocuments);
        stats.put("totalChunks", totalChunks);
        stats.put("averageChunksPerDocument", totalDocuments > 0 ? (totalChunks * 1.0 / totalDocuments) : 0);

        // Embedding status breakdown
        UUID tenantId = userPrincipal.getTenantId();
        Map<String, Long> embeddingBreakdown = new HashMap<>();
        embeddingBreakdown.put("COMPLETED",  documentRepository.countByTenantIdAndEmbeddingStatusAndIsActive(tenantId, "COMPLETED",  true));
        embeddingBreakdown.put("PENDING",    documentRepository.countByTenantIdAndEmbeddingStatusAndIsActive(tenantId, "PENDING",    true));
        embeddingBreakdown.put("PROCESSING", documentRepository.countByTenantIdAndEmbeddingStatusAndIsActive(tenantId, "PROCESSING", true));
        embeddingBreakdown.put("FAILED",     documentRepository.countByTenantIdAndEmbeddingStatusAndIsActive(tenantId, "FAILED",     true));
        stats.put("embeddingStatusBreakdown", embeddingBreakdown);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/llm-usage")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('ANALYTICS_VIEW')")
    @Operation(summary = "Thống kê sử dụng LLM của tenant", 
               description = "Chi tiết về token và request đã sử dụng")
    public ResponseEntity<Map<String, Object>> getLLMUsageStatistics(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> stats = new HashMap<>();
        
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startOfToday  = LocalDateTime.now().toLocalDate().atStartOfDay();
        UUID tenantId = userPrincipal.getTenantId();

        long totalTokens    = chatMessageRepository.sumTokensByTenantId(tenantId);
        long totalRequests  = chatMessageRepository.countRequestsByTenantId(tenantId);
        long tokensMonth    = chatMessageRepository.sumTokensByTenantIdSince(tenantId, startOfMonth);
        long requestsMonth  = chatMessageRepository.countRequestsByTenantIdSince(tenantId, startOfMonth);
        long tokensToday    = chatMessageRepository.sumTokensByTenantIdSince(tenantId, startOfToday);
        long requestsToday  = chatMessageRepository.countRequestsByTenantIdSince(tenantId, startOfToday);

        stats.put("totalTokensUsed", totalTokens);
        stats.put("totalRequests", totalRequests);
        stats.put("tokensThisMonth", tokensMonth);
        stats.put("requestsThisMonth", requestsMonth);
        stats.put("tokensToday", tokensToday);
        stats.put("requestsToday", requestsToday);
        stats.put("averageTokensPerRequest", totalRequests > 0 ? totalTokens / totalRequests : 0);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('ANALYTICS_VIEW')")
    @Operation(summary = "Thống kê users trong tenant",
               description = "Số lượng users và phân bổ theo role")
    public ResponseEntity<Map<String, Object>> getUserStatistics(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> stats = new HashMap<>();

        long totalUsers = userRepository.countByTenantId(userPrincipal.getTenantId());

        stats.put("totalUsers", totalUsers);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/feedback")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('ANALYTICS_VIEW')")
    @Operation(summary = "Get chatbot feedback analytics for tenant")
    public ResponseEntity<Map<String, Object>> getFeedbackAnalytics(
            @AuthenticationPrincipal UserPrincipal userDetails,
            @RequestParam(defaultValue = "10") int limit) {

        UUID tenantId = userDetails.getTenantId();

        Long totalMessages = chatMessageRepository.countRequestsByTenantId(tenantId);
        Long ratedMessages = chatMessageRepository.countRatedMessagesByTenant(tenantId);
        Long positiveRatings = chatMessageRepository.countPositiveRatingsByTenant(tenantId);
        Long negativeRatings = chatMessageRepository.countNegativeRatingsByTenant(tenantId);

        double helpfulPercent = ratedMessages > 0
                ? Math.round((positiveRatings * 100.0 / ratedMessages) * 10.0) / 10.0 : 0.0;
        double notHelpfulPercent = ratedMessages > 0
                ? Math.round((negativeRatings * 100.0 / ratedMessages) * 10.0) / 10.0 : 0.0;

        List<ChatMessage> lowRated = chatMessageRepository.findLowRatedMessagesByTenant(
                tenantId, PageRequest.of(0, limit));

        List<Map<String, Object>> lowRatedList = lowRated.stream().map(msg -> {
            Map<String, Object> item = new HashMap<>();
            item.put("messageId", msg.getId());
            item.put("answer", msg.getContent());
            item.put("rating", msg.getRating());
            item.put("createdAt", msg.getCreatedAt());
            item.put("sourceChunks", msg.getSourceChunks());
            return item;
        }).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("totalMessages", totalMessages);
        response.put("ratedMessages", ratedMessages);
        response.put("positiveRatings", positiveRatings);
        response.put("negativeRatings", negativeRatings);
        response.put("helpfulPercent", helpfulPercent);
        response.put("notHelpfulPercent", notHelpfulPercent);
        response.put("lowRatedResponses", lowRatedList);

        return ResponseEntity.ok(response);
    }
}

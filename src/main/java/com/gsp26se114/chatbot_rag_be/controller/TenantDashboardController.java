package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.repository.*;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenant-admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "12. 📊 Tenant Dashboard & Analytics", 
     description = "Dashboard và thống kê của tenant (token, LLM request, documents) - TENANT_ADMIN")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TenantDashboardController {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    @GetMapping
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
        
        // TODO: Thêm token và LLM request tracking
        // Hiện tại chưa có bảng tracking, sẽ implement sau
        Map<String, Object> llmStats = new HashMap<>();
        llmStats.put("totalTokensUsed", 0); // TODO: Implement token tracking
        llmStats.put("totalRequests", 0);    // TODO: Implement request tracking
        llmStats.put("tokensThisMonth", 0);  // TODO: Implement monthly tracking
        llmStats.put("requestsThisMonth", 0);
        dashboard.put("llmUsage", llmStats);
        
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/documents")
    @Operation(summary = "Thống kê documents của tenant", 
               description = "Chi tiết về tài liệu và knowledge base")
    public ResponseEntity<Map<String, Object>> getDocumentStatistics(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> stats = new HashMap<>();
        
        long totalDocuments = documentRepository.countByTenantId(userPrincipal.getTenantId());
        long totalChunks = documentChunkRepository.countByTenantId(userPrincipal.getTenantId());
        
        stats.put("totalDocuments", totalDocuments);
        stats.put("totalChunks", totalChunks);
        stats.put("averageChunksPerDocument", totalDocuments > 0 ? (totalChunks * 1.0 / totalDocuments) : 0);
        
        // Document status breakdown (if needed)
        // TODO: Add status-based counting if document has status field
        
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/llm-usage")
    @Operation(summary = "Thống kê sử dụng LLM của tenant", 
               description = "Chi tiết về token và request đã sử dụng")
    public ResponseEntity<Map<String, Object>> getLLMUsageStatistics(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> stats = new HashMap<>();
        
        // TODO: Implement actual tracking
        // Cần tạo bảng llm_usage_tracking với các trường:
        // - tenant_id, user_id, tokens_used, request_date, model_name, request_type (chat/embedding)
        
        stats.put("totalTokensUsed", 0);
        stats.put("totalRequests", 0);
        stats.put("tokensThisMonth", 0);
        stats.put("requestsThisMonth", 0);
        stats.put("averageTokensPerRequest", 0);
        stats.put("note", "Token tracking chưa được implement. Cần thêm bảng llm_usage_tracking.");
        
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    @Operation(summary = "Thống kê users trong tenant", 
               description = "Số lượng users và phân bổ theo role")
    public ResponseEntity<Map<String, Object>> getUserStatistics(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> stats = new HashMap<>();
        
        long totalUsers = userRepository.countByTenantId(userPrincipal.getTenantId());
        
        stats.put("totalUsers", totalUsers);
        
        return ResponseEntity.ok(stats);
    }
}

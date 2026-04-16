package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    // ── Per-tenant: all time ──────────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(m.tokensUsed), 0) FROM ChatMessage m WHERE m.tenantId = :tenantId")
    Long sumTokensByTenantId(@Param("tenantId") UUID tenantId);

    /** Each ASSISTANT message = one LLM request */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.tenantId = :tenantId AND m.role = 'ASSISTANT'")
    Long countRequestsByTenantId(@Param("tenantId") UUID tenantId);

    // ── Per-tenant: since a given date ───────────────────────────────────────

    @Query("SELECT COALESCE(SUM(m.tokensUsed), 0) FROM ChatMessage m WHERE m.tenantId = :tenantId AND m.createdAt >= :from")
    Long sumTokensByTenantIdSince(@Param("tenantId") UUID tenantId, @Param("from") LocalDateTime from);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.tenantId = :tenantId AND m.role = 'ASSISTANT' AND m.createdAt >= :from")
    Long countRequestsByTenantIdSince(@Param("tenantId") UUID tenantId, @Param("from") LocalDateTime from);

    // ── Global (all tenants) ─────────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(m.tokensUsed), 0) FROM ChatMessage m")
    Long sumAllTokens();

    @Query("SELECT COALESCE(SUM(m.tokensUsed), 0) FROM ChatMessage m WHERE m.createdAt >= :from")
    Long sumAllTokensSince(@Param("from") LocalDateTime from);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.role = 'ASSISTANT'")
    Long countAllRequests();

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.role = 'ASSISTANT' AND m.createdAt >= :from")
    Long countAllRequestsSince(@Param("from") LocalDateTime from);

    // ── Chat history queries ────────────────────────────────────────────────

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Long countBySessionId(UUID sessionId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.tenantId = :tenantId AND m.userId = :userId AND m.createdAt >= :startOfDay AND m.role = 'USER'")
    Long countTodayMessagesByUser(
        @Param("tenantId") UUID tenantId,
        @Param("userId") UUID userId,
        @Param("startOfDay") LocalDateTime startOfDay
    );

    // ── Feedback / Rating queries ─────────────────────────────────────────────

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.tenantId = :tenantId AND m.role = 'ASSISTANT' AND m.rating IS NOT NULL")
    Long countRatedMessagesByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.tenantId = :tenantId AND m.role = 'ASSISTANT' AND m.rating >= 4")
    Long countPositiveRatingsByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.tenantId = :tenantId AND m.role = 'ASSISTANT' AND m.rating <= 2 AND m.rating IS NOT NULL")
    Long countNegativeRatingsByTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT m FROM ChatMessage m WHERE m.tenantId = :tenantId AND m.role = 'ASSISTANT' AND m.rating <= 2 AND m.rating IS NOT NULL ORDER BY m.rating ASC, m.createdAt DESC")
    List<ChatMessage> findLowRatedMessagesByTenant(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.role = 'ASSISTANT' AND m.rating IS NOT NULL")
    Long countAllRatedMessages();

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.role = 'ASSISTANT' AND m.rating >= 4")
    Long countAllPositiveRatings();

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.role = 'ASSISTANT' AND m.rating <= 2 AND m.rating IS NOT NULL")
    Long countAllNegativeRatings();

    @Query("SELECT DISTINCT m.tenantId FROM ChatMessage m WHERE m.role = 'ASSISTANT'")
    List<UUID> findDistinctTenantIds();
}

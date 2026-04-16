package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    
    // Lấy subscription hiện tại (ACTIVE) của tenant
    Optional<Subscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);
    
    // Lấy tất cả subscriptions ACTIVE của tenant (có thể có nhiều do race condition)
    List<Subscription> findAllByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);
    
    // Lấy tất cả subscriptions của tenant (để debug)
    List<Subscription> findByTenantId(UUID tenantId);
    
    // Lấy tất cả subscriptions của tenant (history - ordered)
    List<Subscription> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    
    // Lấy subscription active hiện tại
    @Query("SELECT s FROM Subscription s WHERE s.tenantId = :tenantId AND s.status = 'ACTIVE' ORDER BY s.createdAt DESC")
    Optional<Subscription> findActiveSubscriptionByTenantId(UUID tenantId);
    
    // Lấy các subscription sắp hết hạn (trong vòng X ngày)
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.endDate <= :date AND s.autoRenew = true")
    List<Subscription> findSubscriptionsExpiringSoon(LocalDateTime date);
    
    // Lấy các subscription đang sắp expire (dùng cho auto-renewal)
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.endDate <= :date AND s.autoRenew = true")
    List<Subscription> findExpiringSubscriptions(LocalDateTime date);
    
    // Lấy các subscription đã hết hạn nhưng chưa đổi status
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.endDate < :now")
    List<Subscription> findExpiredActiveSubscriptions(LocalDateTime now);

    // Lấy các subscription đang trong grace period (endDate đã qua nhưng còn trong kỳ grace)
    @Query("""
        SELECT s FROM Subscription s
        WHERE s.status = 'ACTIVE'
        AND s.endDate < :now
        AND s.endDate >= :graceStart
        """)
    List<Subscription> findSubscriptionsInGracePeriod(
        @Param("now") LocalDateTime now,
        @Param("graceStart") LocalDateTime graceStart
    );

    // Kiểm tra tenant có subscription active không
    boolean existsByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);

    // Kiểm tra plan có đang được subscription nào tham chiếu không
    boolean existsByPlanId(UUID planId);

    @Query("""
            SELECT s
            FROM Subscription s
            WHERE s.createdAt < :beforeTime
            ORDER BY s.createdAt DESC, s.id DESC
            """)
    List<Subscription> findRecentForStaff(@Param("beforeTime") LocalDateTime beforeTime, Pageable pageable);
}

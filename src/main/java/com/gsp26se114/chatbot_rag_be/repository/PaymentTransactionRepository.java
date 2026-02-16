package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.PaymentStatus;
import com.gsp26se114.chatbot_rag_be.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PaymentTransaction entity
 *
 * @author GSP26SE114
 * @version 1.0
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    /**
     * Find payment by unique transaction code
     */
    Optional<PaymentTransaction> findByTransactionCode(String transactionCode);

    /**
     * Find all payments for a specific subscription
     */
    List<PaymentTransaction> findBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);

    /**
     * Find all payments for a specific tenant
     */
    List<PaymentTransaction> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /**
     * Find payments by status
     */
    List<PaymentTransaction> findByStatus(PaymentStatus status);

    /**
     * Find pending payments that have expired
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'PENDING' AND pt.expiresAt < :now")
    List<PaymentTransaction> findExpiredPendingPayments(@Param("now") LocalDateTime now);

    /**
     * Find the latest payment for a subscription
     */
    Optional<PaymentTransaction> findFirstBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);

    /**
     * Find all successful payments for a tenant
     */
    List<PaymentTransaction> findByTenantIdAndStatusOrderByPaidAtDesc(UUID tenantId, PaymentStatus status);

    /**
     * Check if a transaction code already exists
     */
    boolean existsByTransactionCode(String transactionCode);

    /**
     * Find payments by gateway transaction ID
     */
    Optional<PaymentTransaction> findByGatewayTransactionId(String gatewayTransactionId);

    /**
     * Count successful payments for a tenant
     */
    @Query("SELECT COUNT(pt) FROM PaymentTransaction pt WHERE pt.tenantId = :tenantId AND pt.status = 'SUCCESS'")
    long countSuccessfulPaymentsByTenant(@Param("tenantId") UUID tenantId);

    /**
     * Find auto-renewal payments that are pending
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.isAutoRenewal = true AND pt.status = 'PENDING' AND pt.expiresAt > :now")
    List<PaymentTransaction> findPendingAutoRenewals(@Param("now") LocalDateTime now);
}

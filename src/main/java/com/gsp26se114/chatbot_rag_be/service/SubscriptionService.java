package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.*;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {
    
    private final SubscriptionRepository subscriptionRepository;
    private final SePayService sePayService;
    
    /**
     * Tạo trial subscription mặc định khi tenant được approve
     */
    @Transactional
    public Subscription createTrialSubscription(UUID tenantId, UUID createdByAdminId) {
        log.info("Creating trial subscription for tenant: {}", tenantId);
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime trialEnd = now.plusDays(14); // 14 ngày trial
        
        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setTier(SubscriptionTier.TRIAL);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(now);
        subscription.setEndDate(trialEnd);
        subscription.setTrialEndDate(trialEnd);
        subscription.setIsTrial(true);
        subscription.setPrice(BigDecimal.ZERO);
        subscription.setCurrency("VND");
        subscription.setBillingCycle(BillingCycle.MONTHLY);
        subscription.setAutoRenew(false); // Trial không auto renew
        
        // Set usage limits cho TRIAL
        subscription.setMaxUsers(5);
        subscription.setMaxDocuments(100);
        subscription.setMaxStorageGb(5);
        subscription.setMaxApiCalls(1000);
        
        subscription.setCreatedAt(now);
        subscription.setCreatedBy(createdByAdminId);
        subscription.setNotes("Trial subscription - 14 days");
        
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Trial subscription created: {} for tenant: {}", saved.getId(), tenantId);
        
        return saved;
    }
    
    /**
     * Lấy pricing và limits dựa theo tier
     */
    public SubscriptionPricing getPricingByTier(SubscriptionTier tier, BillingCycle cycle) {
        return switch (tier) {
            case TRIAL -> new SubscriptionPricing(BigDecimal.ZERO, 5, 100, 5, 1000);
            case STARTER -> {
                BigDecimal price = switch (cycle) {
                    case MONTHLY -> new BigDecimal("5000"); // TEST: 5k VND for testing
                    case QUARTERLY -> new BigDecimal("13500"); // TEST: 13.5k VND
                    case YEARLY -> new BigDecimal("48000"); // TEST: 48k VND
                };
                yield new SubscriptionPricing(price, 20, 1000, 50, 10000);
            }
            case STANDARD -> {
                BigDecimal price = switch (cycle) {
                    case MONTHLY -> new BigDecimal("1500000"); // 1.5M VND/month
                    case QUARTERLY -> new BigDecimal("4050000"); // 4.05M VND/quarter (10% off)
                    case YEARLY -> new BigDecimal("14400000"); // 14.4M VND/year (20% off)
                };
                yield new SubscriptionPricing(price, 100, 10000, 500, 100000);
            }
            case ENTERPRISE -> {
                BigDecimal price = switch (cycle) {
                    case MONTHLY -> new BigDecimal("5000000"); // 5M VND/month
                    case QUARTERLY -> new BigDecimal("13500000"); // 13.5M VND/quarter (10% off)
                    case YEARLY -> new BigDecimal("48000000"); // 48M VND/year (20% off)
                };
                yield new SubscriptionPricing(price, -1, -1, -1, -1); // Unlimited
            }
        };
    }
    
    /**
     * Record class cho pricing info
     */
    public record SubscriptionPricing(
        BigDecimal price,
        Integer maxUsers,
        Integer maxDocuments,
        Integer maxStorageGb,
        Integer maxApiCalls
    ) {}
    
    /**
     * Upgrade subscription sang tier cao hơn
     * TODO: Implement khi có payment
     */
    @Transactional
    public Subscription upgradeSubscription(UUID tenantId, SubscriptionTier newTier, BillingCycle cycle, UUID upgradedByAdminId) {
        log.info("Upgrading subscription for tenant: {} to tier: {}", tenantId, newTier);
        
        // Tìm subscription hiện tại
        Subscription current = subscriptionRepository.findActiveSubscriptionByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy subscription active cho tenant"));
        
        // Deactivate subscription cũ
        current.setStatus(SubscriptionStatus.CANCELLED);
        current.setCancelledAt(LocalDateTime.now());
        current.setCancelledBy(upgradedByAdminId);
        current.setCancellationReason("Upgraded to " + newTier);
        current.setUpdatedAt(LocalDateTime.now());
        current.setUpdatedBy(upgradedByAdminId);
        subscriptionRepository.save(current);
        
        // Tạo subscription mới
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = switch (cycle) {
            case MONTHLY -> now.plusMonths(1);
            case QUARTERLY -> now.plusMonths(3);
            case YEARLY -> now.plusYears(1);
        };
        
        SubscriptionPricing pricing = getPricingByTier(newTier, cycle);
        
        Subscription newSubscription = new Subscription();
        newSubscription.setTenantId(tenantId);
        newSubscription.setTier(newTier);
        newSubscription.setStatus(SubscriptionStatus.ACTIVE);
        newSubscription.setStartDate(now);
        newSubscription.setEndDate(endDate);
        newSubscription.setIsTrial(false);
        newSubscription.setPrice(pricing.price());
        newSubscription.setCurrency("VND");
        newSubscription.setBillingCycle(cycle);
        newSubscription.setAutoRenew(true);
        newSubscription.setNextBillingDate(endDate);
        
        // Set limits
        newSubscription.setMaxUsers(pricing.maxUsers());
        newSubscription.setMaxDocuments(pricing.maxDocuments());
        newSubscription.setMaxStorageGb(pricing.maxStorageGb());
        newSubscription.setMaxApiCalls(pricing.maxApiCalls());
        
        newSubscription.setCreatedAt(now);
        newSubscription.setCreatedBy(upgradedByAdminId);
        newSubscription.setNotes("Upgraded from " + current.getTier() + " to " + newTier);
        
        Subscription saved = subscriptionRepository.save(newSubscription);
        log.info("New subscription created: {} for tenant: {}", saved.getId(), tenantId);
        
        return saved;
    }
    
    /**
     * Lấy subscription hiện tại của tenant
     */
    public Subscription getActiveSubscription(UUID tenantId) {
        log.info("Getting active subscription for tenant: {}", tenantId);
        Optional<Subscription> subOpt = subscriptionRepository.findActiveSubscriptionByTenantId(tenantId);
        if (subOpt.isEmpty()) {
            log.warn("No active subscription found for tenant: {}", tenantId);
            // Debug: Check all subscriptions for this tenant
            List<Subscription> allSubs = subscriptionRepository.findByTenantId(tenantId);
            log.warn("Found {} total subscriptions for tenant: {}", allSubs.size(), tenantId);
            allSubs.forEach(s -> log.warn("  - Subscription {}: status={}, tier={}", s.getId(), s.getStatus(), s.getTier()));
        }
        return subOpt.orElseThrow(() -> new RuntimeException("Không tìm thấy subscription active cho tenant: " + tenantId));
    }

    // ==================== PAYMENT METHODS ====================

    /**
     * Select a paid plan and initiate payment via SePay.
     * This creates a PENDING subscription and generates QR code for payment.
     *
     * @param tenantId Tenant selecting the plan
     * @param tier Subscription tier (STARTER, STANDARD, ENTERPRISE)
     * @param cycle Billing cycle (MONTHLY, QUARTERLY, YEARLY)
     * @param userId User initiating the purchase
     * @return PaymentTransaction with QR code
     */
    @Transactional
    public com.gsp26se114.chatbot_rag_be.entity.PaymentTransaction selectPaidPlan(
        UUID tenantId, 
        SubscriptionTier tier, 
        BillingCycle cycle, 
        UUID userId
    ) {
        log.info("Tenant {} selecting plan: {} - {}", tenantId, tier, cycle);

        // Validate tier (cannot select TRIAL via this method)
        if (tier == SubscriptionTier.TRIAL) {
            throw new IllegalArgumentException("Cannot purchase TRIAL tier. Trial is auto-assigned.");
        }

        // Check if tenant has active non-trial subscription
        subscriptionRepository.findActiveSubscriptionByTenantId(tenantId)
            .ifPresent(existing -> {
                if (!existing.getIsTrial()) {
                    throw new IllegalStateException("Tenant already has an active paid subscription. Please cancel first.");
                }
            });

        // Get pricing info
        SubscriptionPricing pricing = getPricingByTier(tier, cycle);

        // Calculate end date
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = switch (cycle) {
            case MONTHLY -> now.plusMonths(1);
            case QUARTERLY -> now.plusMonths(3);
            case YEARLY -> now.plusYears(1);
        };

        // Create PENDING subscription
        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setTier(tier);
        subscription.setStatus(SubscriptionStatus.SUSPENDED); // SUSPENDED until payment confirmed
        subscription.setStartDate(now);
        subscription.setEndDate(endDate);
        subscription.setIsTrial(false);
        subscription.setPrice(pricing.price());
        subscription.setCurrency("VND");
        subscription.setBillingCycle(cycle);
        subscription.setAutoRenew(true);
        subscription.setNextBillingDate(endDate);
        subscription.setMaxUsers(pricing.maxUsers());
        subscription.setMaxDocuments(pricing.maxDocuments());
        subscription.setMaxStorageGb(pricing.maxStorageGb());
        subscription.setMaxApiCalls(pricing.maxApiCalls());
        subscription.setCreatedAt(now);
        subscription.setCreatedBy(userId);
        subscription.setPaymentMethod("BANK_TRANSFER");
        subscription.setNotes("Awaiting payment confirmation");

        Subscription savedSubscription = subscriptionRepository.save(subscription);
        log.info("Created pending subscription: {}", savedSubscription.getId());

        // Generate payment and QR code via SePay
        return sePayService.createPayment(savedSubscription, userId);
    }

    /**
     * Cancel subscription (mark as cancelled, remain active until end date).
     *
     * @param tenantId Tenant ID
     * @param userId User requesting cancellation
     * @param reason Cancellation reason
     */
    @Transactional
    public void cancelSubscription(UUID tenantId, UUID userId, String reason) {
        log.info("Cancelling subscription for tenant: {}", tenantId);

        Subscription subscription = getActiveSubscription(tenantId);

        if (subscription.getIsTrial()) {
            throw new IllegalStateException("Cannot cancel trial subscription. It will expire automatically.");
        }

        subscription.setAutoRenew(false);
        subscription.setCancelledAt(LocalDateTime.now());
        subscription.setCancelledBy(userId);
        subscription.setCancellationReason(reason);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscription.setUpdatedBy(userId);
        subscription.setNotes(subscription.getNotes() + " | Cancelled by user: " + reason);

        subscriptionRepository.save(subscription);
        log.info("Subscription cancelled: {}. Will expire at: {}", subscription.getId(), subscription.getEndDate());
    }

    /**
     * Toggle auto-renewal on/off.
     *
     * @param tenantId Tenant ID
     * @param autoRenew True to enable, false to disable
     * @param userId User making the change
     */
    @Transactional
    public void toggleAutoRenew(UUID tenantId, boolean autoRenew, UUID userId) {
        log.info("Toggling auto-renew for tenant: {} to {}", tenantId, autoRenew);

        Subscription subscription = getActiveSubscription(tenantId);

        if (subscription.getIsTrial()) {
            throw new IllegalStateException("Trial subscriptions do not support auto-renewal.");
        }

        subscription.setAutoRenew(autoRenew);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscription.setUpdatedBy(userId);

        subscriptionRepository.save(subscription);
        log.info("Auto-renew set to {} for subscription: {}", autoRenew, subscription.getId());
    }

    /**
     * Process auto-renewal for expiring subscriptions.
     * Should be called by scheduled job daily.
     *
     * @return Number of renewals processed
     */
    @Transactional
    public int processAutoRenewals() {
        log.info("Processing auto-renewals...");

        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
        var expiringSubscriptions = subscriptionRepository.findExpiringSubscriptions(tomorrow);

        int processedCount = 0;
        for (Subscription subscription : expiringSubscriptions) {
            if (subscription.getAutoRenew() && !subscription.getIsTrial()) {
                try {
                    // Create renewal payment
                    com.gsp26se114.chatbot_rag_be.entity.PaymentTransaction payment = 
                        sePayService.createPayment(subscription, subscription.getCreatedBy());
                    payment.setIsAutoRenewal(true);

                    log.info("Created auto-renewal payment for subscription: {}", subscription.getId());
                    processedCount++;

                    // TODO: Send email notification to tenant with QR code
                } catch (Exception e) {
                    log.error("Failed to process auto-renewal for subscription: {}", subscription.getId(), e);
                }
            }
        }

        log.info("Processed {} auto-renewals", processedCount);
        return processedCount;
    }
}

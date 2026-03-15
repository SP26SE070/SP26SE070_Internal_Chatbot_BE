package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.*;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionPlanRepository;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
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
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SePayService sePayService;
    private final TenantRepository tenantRepository;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    
    /**
     * Tạo trial subscription mặc định khi tenant được approve
     */
    @Transactional
    public Subscription createTrialSubscription(UUID tenantId, UUID createdByAdminId) {
        log.info("Creating trial subscription for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        if (subscriptionRepository.findActiveSubscriptionByTenantId(tenantId).isPresent()) {
            throw new IllegalStateException("Tenant đang có subscription active, không thể kích hoạt trial.");
        }

        if (Boolean.TRUE.equals(tenant.getTrialUsed())) {
            throw new IllegalStateException("Tenant đã dùng trial trước đó, không thể cấp lại.");
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime trialEnd = now.plusDays(14); // 14 ngày trial
        SubscriptionPlan trialPlan = getActivePlanByTier(SubscriptionTier.TRIAL);
        
        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setPlanId(trialPlan.getId());
        subscription.setTier(SubscriptionTier.TRIAL);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(now);
        subscription.setEndDate(trialEnd);
        subscription.setTrialEndDate(trialEnd);
        subscription.setIsTrial(true);
        subscription.setPrice(resolvePriceByCycle(trialPlan, BillingCycle.MONTHLY));
        subscription.setCurrency(trialPlan.getCurrency());
        subscription.setBillingCycle(BillingCycle.MONTHLY);
        subscription.setAutoRenew(false); // Trial không auto renew
        applyPlanSnapshot(subscription, trialPlan);
        
        subscription.setCreatedAt(now);
        subscription.setCreatedBy(createdByAdminId);
        subscription.setNotes("Trial subscription - 14 days");
        
        Subscription saved = subscriptionRepository.save(subscription);

        tenant.setSubscriptionId(saved.getId());
        tenant.setIsTrial(true);
        tenant.setTrialUsed(true);
        tenant.setUpdatedAt(now);
        tenantRepository.save(tenant);

        log.info("Trial subscription created: {} for tenant: {}", saved.getId(), tenantId);
        
        return saved;
    }
    
    private SubscriptionPlan getActivePlanByTier(SubscriptionTier tier) {
        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(tier.name())
                .orElseThrow(() -> new IllegalArgumentException("Plan không tồn tại cho tier: " + tier));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new IllegalStateException("Plan đang inactive: " + tier);
        }

        return plan;
    }

    private BigDecimal resolvePriceByCycle(SubscriptionPlan plan, BillingCycle cycle) {
        return switch (cycle) {
            case MONTHLY -> plan.getMonthlyPrice();
            case QUARTERLY -> plan.getQuarterlyPrice();
            case YEARLY -> plan.getYearlyPrice();
        };
    }

    private void applyPlanSnapshot(Subscription subscription, SubscriptionPlan plan) {
        subscription.setMaxUsers(plan.getMaxUsers());
        subscription.setMaxDocuments(plan.getMaxDocuments());
        subscription.setMaxStorageGb(plan.getMaxStorageGb());
        subscription.setMaxApiCalls(plan.getMaxApiCalls());
        subscription.setMaxChatbotRequests(plan.getMaxChatbotRequests());
        subscription.setMaxRagDocuments(plan.getMaxRagDocuments());
        subscription.setMaxAiTokens(plan.getMaxAiTokens().longValue());
        subscription.setContextWindowTokens(plan.getContextWindowTokens());
        subscription.setRagChunkSize(plan.getRagChunkSize());
        subscription.setAiModel(plan.getAiModel());
        subscription.setEmbeddingModel(plan.getEmbeddingModel());
        subscription.setEnableRag(true);
    }
    
    /**
     * Upgrade subscription sang tier cao hơn.
     * Tạo payment để tenant quét QR chuyển khoản, subscription chỉ ACTIVE sau khi webhook xác nhận.
     */
    @Transactional
    public com.gsp26se114.chatbot_rag_be.entity.PaymentTransaction upgradeSubscription(
            UUID tenantId, SubscriptionTier newTier, BillingCycle cycle, UUID upgradedByAdminId) {
        log.info("Upgrading subscription for tenant: {} to tier: {}", tenantId, newTier);

        if (newTier == SubscriptionTier.TRIAL) {
            throw new IllegalArgumentException("Cannot upgrade to TRIAL tier.");
        }

        // Tìm subscription hiện tại
        Subscription current = subscriptionRepository.findActiveSubscriptionByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy subscription active cho tenant"));

        // Cancel subscription cũ
        current.setStatus(SubscriptionStatus.CANCELLED);
        current.setCancelledAt(LocalDateTime.now());
        current.setCancelledBy(upgradedByAdminId);
        current.setCancellationReason("Upgraded to " + newTier);
        current.setUpdatedAt(LocalDateTime.now());
        current.setUpdatedBy(upgradedByAdminId);
        subscriptionRepository.save(current);

        // Tạo subscription mới ở trạng thái SUSPENDED, chờ thanh toán
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = switch (cycle) {
            case MONTHLY -> now.plusMonths(1);
            case QUARTERLY -> now.plusMonths(3);
            case YEARLY -> now.plusYears(1);
        };
        SubscriptionPlan plan = getActivePlanByTier(newTier);

        Subscription newSubscription = new Subscription();
        newSubscription.setTenantId(tenantId);
        newSubscription.setPlanId(plan.getId());
        newSubscription.setTier(newTier);
        newSubscription.setStatus(SubscriptionStatus.SUSPENDED);
        newSubscription.setStartDate(now);
        newSubscription.setEndDate(endDate);
        newSubscription.setIsTrial(false);
        newSubscription.setPrice(resolvePriceByCycle(plan, cycle));
        newSubscription.setCurrency(plan.getCurrency());
        newSubscription.setBillingCycle(cycle);
        newSubscription.setAutoRenew(true);
        newSubscription.setNextBillingDate(endDate);
        applyPlanSnapshot(newSubscription, plan);
        newSubscription.setCreatedAt(now);
        newSubscription.setCreatedBy(upgradedByAdminId);
        newSubscription.setPaymentMethod("BANK_TRANSFER");
        newSubscription.setNotes("Upgraded from " + current.getTier() + " to " + newTier + " — awaiting payment");

        Subscription saved = subscriptionRepository.save(newSubscription);
        log.info("Created pending upgrade subscription: {} for tenant: {}", saved.getId(), tenantId);

        // Tạo payment + QR code qua SePay
        return sePayService.createPayment(saved, upgradedByAdminId);
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
        SubscriptionPlan plan = getActivePlanByTier(tier);

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
        subscription.setPlanId(plan.getId());
        subscription.setTier(tier);
        subscription.setStatus(SubscriptionStatus.SUSPENDED); // SUSPENDED until payment confirmed
        subscription.setStartDate(now);
        subscription.setEndDate(endDate);
        subscription.setIsTrial(false);
        subscription.setPrice(resolvePriceByCycle(plan, cycle));
        subscription.setCurrency(plan.getCurrency());
        subscription.setBillingCycle(cycle);
        subscription.setAutoRenew(true);
        subscription.setNextBillingDate(endDate);
        applyPlanSnapshot(subscription, plan);
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

                    // Send email notification to tenant with QR code
                    tenantRepository.findById(subscription.getTenantId()).ifPresent(tenant -> {
                        if (tenant.getContactEmail() != null) {
                            try {
                                String html = emailTemplateService.generateSubscriptionRenewalEmail(
                                    tenant.getName(),
                                    subscription.getTier().name(),
                                    subscription.getBillingCycle().name(),
                                    subscription.getPrice().toPlainString(),
                                    payment.getTransactionCode(),
                                    payment.getQrImageUrl(),
                                    subscription.getEndDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                                    payment.getExpiresAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                                );
                                emailService.sendHtmlEmail(
                                    tenant.getContactEmail(),
                                    "🔔 Gia hạn subscription - " + subscription.getTier().name(),
                                    html
                                );
                                log.info("Renewal email sent to: {}", tenant.getContactEmail());
                            } catch (Exception emailEx) {
                                log.error("Failed to send renewal email for subscription: {}", subscription.getId(), emailEx);
                            }
                        }
                    });
                } catch (Exception e) {
                    log.error("Failed to process auto-renewal for subscription: {}", subscription.getId(), e);
                }
            }
        }

        log.info("Processed {} auto-renewals", processedCount);
        return processedCount;
    }
}

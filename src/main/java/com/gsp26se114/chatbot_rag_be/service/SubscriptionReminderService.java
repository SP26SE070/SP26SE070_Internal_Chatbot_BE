package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.PaymentTransaction;
import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionReminderService {

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final SePayService sePayService;
    private final SubscriptionService subscriptionService;

    private static final int REMINDER_DAYS_BEFORE = 3;
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Daily job at 09:00 AM — send reminder emails for subscriptions
         * exactly 3 days before end date.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendExpiryReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate reminderTargetDate = now.toLocalDate().plusDays(REMINDER_DAYS_BEFORE);
        LocalDateTime threshold = now.plusDays(REMINDER_DAYS_BEFORE + 1L);

        List<Subscription> candidates = subscriptionRepository
                .findSubscriptionsExpiringBefore(threshold, now);
        List<Subscription> expiringSoon = candidates.stream()
            .filter(subscription -> subscription.getEndDate() != null)
            .filter(subscription -> subscription.getEndDate().toLocalDate().isEqual(reminderTargetDate))
            .toList();

        if (expiringSoon.isEmpty()) {
            log.info("Subscription reminder check: no subscriptions hitting {}-day reminder window today",
                    REMINDER_DAYS_BEFORE);
            return;
        }

        int sent = 0;
        int failed = 0;

        for (Subscription subscription : expiringSoon) {
            try {
                if (Boolean.TRUE.equals(subscription.getIsTrial())) {
                    continue;
                }

                if (!Boolean.TRUE.equals(subscription.getAutoRenew())) {
                    continue;
                }

                Tenant tenant = tenantRepository.findById(subscription.getTenantId())
                        .orElse(null);
                if (tenant == null) {
                    log.warn("Tenant not found for subscription: {}", subscription.getId());
                    continue;
                }

                if (tenant.getContactEmail() == null || tenant.getContactEmail().isBlank()) {
                    log.warn("Skip reminder: tenant {} has no contact email", tenant.getId());
                    continue;
                }

                PaymentTransaction payment = sePayService.createOrReusePendingPayment(
                        subscription,
                        subscription.getUpdatedBy() != null ? subscription.getUpdatedBy() : subscription.getCreatedBy(),
                        Boolean.TRUE.equals(subscription.getAutoRenew())
                );

                subscriptionService.sendRenewalReminderEmail(subscription, payment);

                String expiryDate = subscription.getEndDate() != null
                        ? subscription.getEndDate().format(DATE_FORMATTER)
                        : "—";
                log.info("Sent {}-day reminder (with QR) to tenant: {} ({}), expires: {}",
                    REMINDER_DAYS_BEFORE,
                        tenant.getName(), tenant.getContactEmail(), expiryDate);
                sent++;

            } catch (Exception e) {
                log.warn("Failed to send expiry reminder for subscription {}: {}",
                        subscription.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Subscription reminder job complete ({}-day window): {} sent, {} failed",
            REMINDER_DAYS_BEFORE, sent, failed);
    }
}

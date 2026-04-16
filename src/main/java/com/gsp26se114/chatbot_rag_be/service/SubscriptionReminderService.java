package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionReminderService {

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final EmailService emailService;

    private static final int REMINDER_DAYS_BEFORE = 3;
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Daily job at 09:00 AM — send reminder emails for subscriptions
     * expiring within 3 days.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendExpiryReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusDays(REMINDER_DAYS_BEFORE);

        List<Subscription> expiringSoon = subscriptionRepository
                .findSubscriptionsExpiringBefore(threshold, now);

        if (expiringSoon.isEmpty()) {
            log.info("Subscription reminder check: no subscriptions expiring within {} days",
                    REMINDER_DAYS_BEFORE);
            return;
        }

        int sent = 0;
        int failed = 0;

        for (Subscription subscription : expiringSoon) {
            try {
                Tenant tenant = tenantRepository.findById(subscription.getTenantId())
                        .orElse(null);
                if (tenant == null) {
                    log.warn("Tenant not found for subscription: {}", subscription.getId());
                    continue;
                }

                String expiryDate = subscription.getEndDate().format(DATE_FORMATTER);
                String subject = "⚠️ Gói đăng ký của " + tenant.getName() +
                                 " sắp hết hạn vào " + expiryDate;
                String body = String.format("""
                        <html><body style='font-family: Arial; padding: 20px;'>
                        <h2>⚠️ Thông báo gia hạn gói dịch vụ</h2>
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Gói <strong>%s</strong> của công ty bạn sẽ hết hạn vào <strong>%s</strong>.</p>
                        <p>Vui lòng đăng nhập vào hệ thống và gia hạn gói dịch vụ để tiếp tục sử dụng.</p>
                        <p>Sau khi hết hạn, bạn sẽ có <strong>7 ngày</strong> (grace period) để gia hạn
                        trước khi tài khoản bị khóa hoàn toàn.</p>
                        <br/>
                        <p>Trân trọng,<br/>Chatbot RAG Team</p>
                        </body></html>
                        """,
                        tenant.getRepresentativeName() != null
                            ? tenant.getRepresentativeName() : tenant.getName(),
                        subscription.getTier().name(),
                        expiryDate
                );

                emailService.sendHtmlEmail(tenant.getContactEmail(), subject, body);
                log.info("Sent expiry reminder to tenant: {} ({}), expires: {}",
                        tenant.getName(), tenant.getContactEmail(), expiryDate);
                sent++;

            } catch (Exception e) {
                log.warn("Failed to send expiry reminder for subscription {}: {}",
                        subscription.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Subscription reminder job complete: {} sent, {} failed", sent, failed);
    }
}

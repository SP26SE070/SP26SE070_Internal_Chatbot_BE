package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.entity.SubscriptionStatus;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionExpiryService {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Daily job to mark expired subscriptions.
     * Runs at 01:00 every day.
     * Cron: second minute hour day month weekday
     *
     * Step 1: Find subscriptions that have passed their endDate while still ACTIVE.
     * Step 2: Separate into grace period vs. truly expired based on gracePeriodDays.
     * Step 3: Mark grace period subscriptions as GRACE_PERIOD.
     * Step 4: Mark fully expired subscriptions as EXPIRED.
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void markExpiredSubscriptions() {
        LocalDateTime now = LocalDateTime.now();

        // Step 1: Find truly expired subscriptions (past grace period)
        List<Subscription> hardExpired = subscriptionRepository
                .findExpiredActiveSubscriptions(now);

        // Step 2: Separate into grace period vs truly expired
        List<Subscription> inGrace = new java.util.ArrayList<>();
        List<Subscription> fullyExpired = new java.util.ArrayList<>();

        for (Subscription sub : hardExpired) {
            int graceDays = sub.getGracePeriodDays() != null ? sub.getGracePeriodDays() : 7;
            LocalDateTime graceEnd = sub.getEndDate().plusDays(graceDays);
            if (now.isBefore(graceEnd)) {
                inGrace.add(sub);
            } else {
                fullyExpired.add(sub);
            }
        }

        // Step 3: Mark grace period subscriptions
        for (Subscription sub : inGrace) {
            sub.setStatus(SubscriptionStatus.GRACE_PERIOD);
            sub.setUpdatedAt(now);
        }

        // Step 4: Mark fully expired subscriptions
        for (Subscription sub : fullyExpired) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setUpdatedAt(now);
        }

        if (!inGrace.isEmpty()) {
            subscriptionRepository.saveAll(inGrace);
            log.info("Marked {} subscription(s) as GRACE_PERIOD", inGrace.size());
        }
        if (!fullyExpired.isEmpty()) {
            subscriptionRepository.saveAll(fullyExpired);
            log.info("Marked {} subscription(s) as EXPIRED", fullyExpired.size());
        }
        if (inGrace.isEmpty() && fullyExpired.isEmpty()) {
            log.info("Subscription expiry check: no expired subscriptions found");
        }
    }
}
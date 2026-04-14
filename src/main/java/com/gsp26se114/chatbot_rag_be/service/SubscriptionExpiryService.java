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
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void markExpiredSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> expired = subscriptionRepository.findExpiredActiveSubscriptions(now);

        if (expired.isEmpty()) {
            log.info("Subscription expiry check: no expired subscriptions found");
            return;
        }

        for (Subscription subscription : expired) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscription.setUpdatedAt(now);
        }

        subscriptionRepository.saveAll(expired);
        log.info("Marked {} subscription(s) as EXPIRED", expired.size());
    }
}
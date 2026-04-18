package com.gsp26se114.chatbot_rag_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentExpiryService {

    private final SePayService sePayService;

    /**
     * Run every 10 minutes to avoid stale transactions staying in PENDING for too long.
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void expireStalePendingPayments() {
        int updated = sePayService.markExpiredPayments();
        if (updated > 0) {
            log.info("Payment expiry job: marked {} stale pending payment(s) as EXPIRED", updated);
        }
    }
}

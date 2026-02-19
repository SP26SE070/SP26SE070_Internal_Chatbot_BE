package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.repository.BlacklistedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service để tự động dọn dẹp các token hết hạn trong blacklist
 * Chạy mỗi 1 giờ để giảm kích thước database
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    /**
     * Tự động xóa các token hết hạn trong blacklist mỗi 1 giờ
     * Cron expression: 0 0 * * * * = Chạy vào giây 0, phút 0 của mỗi giờ
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        
        // Tìm tất cả token đã hết hạn
        var expiredTokens = blacklistedTokenRepository.findAll().stream()
                .filter(token -> token.getExpiryDate().isBefore(now))
                .toList();
        
        if (!expiredTokens.isEmpty()) {
            blacklistedTokenRepository.deleteAll(expiredTokens);
            log.info("Cleaned up {} expired tokens from blacklist", expiredTokens.size());
        }
    }
}

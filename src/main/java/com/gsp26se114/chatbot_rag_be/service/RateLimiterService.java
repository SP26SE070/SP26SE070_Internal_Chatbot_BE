package com.gsp26se114.chatbot_rag_be.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

@Slf4j
@Service
public class RateLimiterService {

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_MILLIS = 60_000L; // 60 seconds

    public record RateLimitResult(boolean allowed, int remainingRequests, int retryAfterSeconds) {}

    private record RateLimitKey(UUID tenantId, UUID userId) {}

    private static class Bucket {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    }

    private final ConcurrentHashMap<RateLimitKey, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitResult checkLimit(UUID tenantId, UUID userId) {
        RateLimitKey key = new RateLimitKey(tenantId, userId);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());

        synchronized (bucket) {
            long now = System.currentTimeMillis();
            long windowStart = bucket.windowStart.get();

            // Reset window if expired
            if (now - windowStart >= WINDOW_MILLIS) {
                bucket.windowStart.set(now);
                bucket.count.set(0);
            }

            int current = bucket.count.incrementAndGet();
            if (current > MAX_REQUESTS) {
                long elapsed = now - bucket.windowStart.get();
                int retryAfter = (int) ((WINDOW_MILLIS - elapsed) / 1000) + 1;
                log.warn("Rate limit exceeded for user {} in tenant {}", userId, tenantId);
                return new RateLimitResult(false, 0, retryAfter);
            }

            return new RateLimitResult(true, MAX_REQUESTS - current, 0);
        }
    }
}
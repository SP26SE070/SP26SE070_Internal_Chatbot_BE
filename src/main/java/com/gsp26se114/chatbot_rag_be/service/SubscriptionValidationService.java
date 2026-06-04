package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.exception.SubscriptionLimitExceededException;
import com.gsp26se114.chatbot_rag_be.repository.ChatMessageRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentRepository;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionValidationService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ChatMessageRepository chatMessageRepository;

    public void validateUserCreation(UUID tenantId) {
        Subscription subscription = activeSubscriptionOrThrow(tenantId);
        int maxUsers = requireIntegerLimit(subscription.getMaxUsers(), "max_users", tenantId);

        long currentUsers = userRepository.countByTenantIdAndIsActive(tenantId, true);
        enforceLimit("max_users", currentUsers, maxUsers);
    }

    public void validateBulkUserCreation(UUID tenantId, int additionalCount) {
        if (additionalCount <= 0) {
            return;
        }
        Subscription subscription = activeSubscriptionOrThrow(tenantId);
        int maxUsers = requireIntegerLimit(subscription.getMaxUsers(), "max_users", tenantId);

        long currentUsers = userRepository.countByTenantIdAndIsActive(tenantId, true);
        long projectedUsers = currentUsers + additionalCount;
        if (projectedUsers > maxUsers) {
            throw new SubscriptionLimitExceededException("max_users", projectedUsers, maxUsers);
        }
    }

    public void validateDocumentUpload(UUID tenantId) {
        Subscription subscription = activeSubscriptionOrThrow(tenantId);
        int maxDocuments = requireIntegerLimit(subscription.getMaxDocuments(), "max_documents", tenantId);

        long currentDocuments = documentRepository.countByTenantIdAndIsActive(tenantId, true);
        enforceLimit("max_documents", currentDocuments, maxDocuments);
    }

    public void validateChatUsage(UUID tenantId) {
        Subscription subscription = activeSubscriptionOrThrow(tenantId);
        int maxChatbotRequests = requireIntegerLimit(
                subscription.getMaxChatbotRequests(), "max_chatbot_requests", tenantId);
        long maxAiTokens = requireLongLimit(subscription.getMaxAiTokens(), "max_ai_tokens", tenantId);

        LocalDateTime startOfMonth = LocalDateTime.now()
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        long currentRequests = chatMessageRepository.countRequestsByTenantIdSince(tenantId, startOfMonth);
        enforceLimit("max_chatbot_requests", currentRequests, maxChatbotRequests);

        long currentTokens = chatMessageRepository.sumTokensByTenantIdSince(tenantId, startOfMonth);
        enforceLimit("max_ai_tokens", currentTokens, maxAiTokens);
    }

    private Subscription activeSubscriptionOrThrow(UUID tenantId) {
        return subscriptionRepository.findActiveSubscriptionByTenantId(tenantId)
                .orElseThrow(() -> SubscriptionLimitExceededException.noActiveSubscription(tenantId));
    }

    private int requireIntegerLimit(Integer limit, String limitName, UUID tenantId) {
        if (limit == null) {
            throw SubscriptionLimitExceededException.missingLimit(limitName, tenantId);
        }
        return limit;
    }

    private long requireLongLimit(Long limit, String limitName, UUID tenantId) {
        if (limit == null) {
            throw SubscriptionLimitExceededException.missingLimit(limitName, tenantId);
        }
        return limit;
    }

    private void enforceLimit(String limitName, long currentUsage, long limit) {
        if (currentUsage >= limit) {
            throw new SubscriptionLimitExceededException(limitName, currentUsage, limit);
        }
    }
}

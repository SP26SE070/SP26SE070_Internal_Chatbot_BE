package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.repository.DocumentRepository;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionValidationService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    public void validateUserCreation(UUID tenantId) {
        validateBulkUserCreation(tenantId, 1);
    }

    public void validateBulkUserCreation(UUID tenantId, int additionalCount) {
        if (additionalCount <= 0) {
            return;
        }
        Subscription subscription = subscriptionRepository
                .findActiveSubscriptionByTenantId(tenantId)
                .orElse(null);

        if (subscription == null || subscription.getMaxUsers() == null) {
            return;
        }

        long currentUsers = userRepository.countByTenantIdAndIsActive(tenantId, true);
        if (currentUsers + additionalCount > subscription.getMaxUsers()) {
            long remaining = Math.max(0, subscription.getMaxUsers() - currentUsers);
            throw new RuntimeException(
                    "Không thể thêm " + additionalCount + " người dùng. "
                            + "Giới hạn gói: " + subscription.getMaxUsers()
                            + ", hiện có: " + currentUsers
                            + ", còn có thể thêm: " + remaining + "."
            );
        }
    }

    public void validateDocumentUpload(UUID tenantId) {
        Subscription subscription = subscriptionRepository
                .findActiveSubscriptionByTenantId(tenantId)
                .orElse(null);

        if (subscription == null || subscription.getMaxDocuments() == null) {
            return; // no subscription or no limit set — allow
        }

        Long currentDocs = documentRepository.countByTenantIdAndIsActive(tenantId, true);
        if (currentDocs >= subscription.getMaxDocuments()) {
            throw new RuntimeException(
                "Đã đạt giới hạn số lượng tài liệu (" + subscription.getMaxDocuments() +
                ") theo gói đăng ký hiện tại."
            );
        }
    }
}

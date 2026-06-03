package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.exception.SubscriptionLimitExceededException;
import com.gsp26se114.chatbot_rag_be.repository.ChatMessageRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentRepository;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionValidationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private SubscriptionValidationService subscriptionValidationService;

    @Test
    void validateUserCreationAllowsWhenJustUnderLimit() {
        stubActiveSubscription();
        when(userRepository.countByTenantIdAndIsActive(TENANT_ID, true)).thenReturn(4L);

        assertThatCode(() -> subscriptionValidationService.validateUserCreation(TENANT_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void validateUserCreationBlocksAtLimit() {
        stubActiveSubscription();
        when(userRepository.countByTenantIdAndIsActive(TENANT_ID, true)).thenReturn(5L);

        assertThatThrownBy(() -> subscriptionValidationService.validateUserCreation(TENANT_ID))
                .isInstanceOf(SubscriptionLimitExceededException.class)
                .hasMessageContaining("max_users")
                .hasMessageContaining("current=5")
                .hasMessageContaining("limit=5");
    }

    @Test
    void validateDocumentUploadAllowsWhenJustUnderLimit() {
        stubActiveSubscription();
        when(documentRepository.countByTenantIdAndIsActive(TENANT_ID, true)).thenReturn(2L);

        assertThatCode(() -> subscriptionValidationService.validateDocumentUpload(TENANT_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void validateDocumentUploadBlocksAtLimit() {
        stubActiveSubscription();
        when(documentRepository.countByTenantIdAndIsActive(TENANT_ID, true)).thenReturn(3L);

        assertThatThrownBy(() -> subscriptionValidationService.validateDocumentUpload(TENANT_ID))
                .isInstanceOf(SubscriptionLimitExceededException.class)
                .hasMessageContaining("max_documents")
                .hasMessageContaining("current=3")
                .hasMessageContaining("limit=3");
    }

    @Test
    void validateChatUsageAllowsWhenJustUnderRequestAndTokenLimits() {
        stubActiveSubscription();
        when(chatMessageRepository.countRequestsByTenantIdSince(eq(TENANT_ID), any(LocalDateTime.class)))
                .thenReturn(9L);
        when(chatMessageRepository.sumTokensByTenantIdSince(eq(TENANT_ID), any(LocalDateTime.class)))
                .thenReturn(99L);

        assertThatCode(() -> subscriptionValidationService.validateChatUsage(TENANT_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void validateChatUsageBlocksAtRequestLimit() {
        stubActiveSubscription();
        when(chatMessageRepository.countRequestsByTenantIdSince(eq(TENANT_ID), any(LocalDateTime.class)))
                .thenReturn(10L);

        assertThatThrownBy(() -> subscriptionValidationService.validateChatUsage(TENANT_ID))
                .isInstanceOf(SubscriptionLimitExceededException.class)
                .hasMessageContaining("max_chatbot_requests")
                .hasMessageContaining("current=10")
                .hasMessageContaining("limit=10");
    }

    @Test
    void validateChatUsageBlocksAtTokenLimit() {
        stubActiveSubscription();
        when(chatMessageRepository.countRequestsByTenantIdSince(eq(TENANT_ID), any(LocalDateTime.class)))
                .thenReturn(9L);
        when(chatMessageRepository.sumTokensByTenantIdSince(eq(TENANT_ID), any(LocalDateTime.class)))
                .thenReturn(100L);

        assertThatThrownBy(() -> subscriptionValidationService.validateChatUsage(TENANT_ID))
                .isInstanceOf(SubscriptionLimitExceededException.class)
                .hasMessageContaining("max_ai_tokens")
                .hasMessageContaining("current=100")
                .hasMessageContaining("limit=100");
    }

    @Test
    void validateDocumentUploadFailsClosedWithoutActiveSubscription() {
        when(subscriptionRepository.findActiveSubscriptionByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionValidationService.validateDocumentUpload(TENANT_ID))
                .isInstanceOf(SubscriptionLimitExceededException.class)
                .hasMessageContaining("No ACTIVE subscription");
    }

    private void stubActiveSubscription() {
        Subscription subscription = new Subscription();
        subscription.setMaxUsers(5);
        subscription.setMaxDocuments(3);
        subscription.setMaxChatbotRequests(10);
        subscription.setMaxAiTokens(100L);
        when(subscriptionRepository.findActiveSubscriptionByTenantId(TENANT_ID))
                .thenReturn(Optional.of(subscription));
    }
}

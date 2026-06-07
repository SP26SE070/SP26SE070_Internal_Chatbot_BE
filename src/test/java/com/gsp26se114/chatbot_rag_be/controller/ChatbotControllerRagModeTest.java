package com.gsp26se114.chatbot_rag_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsp26se114.chatbot_rag_be.entity.ChatMessage;
import com.gsp26se114.chatbot_rag_be.entity.ChatSession;
import com.gsp26se114.chatbot_rag_be.entity.ChatbotConfig;
import com.gsp26se114.chatbot_rag_be.entity.DocumentChunkEntity;
import com.gsp26se114.chatbot_rag_be.entity.DocumentEntity;
import com.gsp26se114.chatbot_rag_be.payload.request.ChatRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.ChatResponse;
import com.gsp26se114.chatbot_rag_be.repository.ChatMessageRepository;
import com.gsp26se114.chatbot_rag_be.repository.ChatbotConfigRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentChunkRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.ChatHistoryService;
import com.gsp26se114.chatbot_rag_be.service.ChunkEmbeddingVectorSchemaService;
import com.gsp26se114.chatbot_rag_be.service.DocumentAccessPolicy;
import com.gsp26se114.chatbot_rag_be.service.GeminiChatService;
import com.gsp26se114.chatbot_rag_be.service.RateLimiterService;
import com.gsp26se114.chatbot_rag_be.service.SubscriptionValidationService;
import com.gsp26se114.chatbot_rag_be.service.TenantEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotControllerRagModeTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DOCUMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock private TenantEmbeddingService embeddingService;
    @Mock private ChunkEmbeddingVectorSchemaService chunkEmbeddingVectorSchemaService;
    @Mock private GeminiChatService geminiChatService;
    @Mock private DocumentChunkRepository chunkRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private ChatHistoryService chatHistoryService;
    @Mock private ChatbotConfigRepository chatbotConfigRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private DocumentAccessPolicy documentAccessPolicy;
    @Mock private RateLimiterService rateLimiterService;
    @Mock private SubscriptionValidationService subscriptionValidationService;

    private ChatbotController controller;
    private UserPrincipal user;

    @BeforeEach
    void setUp() {
        controller = new ChatbotController(
                embeddingService,
                chunkEmbeddingVectorSchemaService,
                geminiChatService,
                chunkRepository,
                documentRepository,
                chatHistoryService,
                new ObjectMapper(),
                chatbotConfigRepository,
                chatMessageRepository,
                documentAccessPolicy,
                rateLimiterService,
                subscriptionValidationService
        );
        ReflectionTestUtils.setField(controller, "storageDimension", 3);
        ReflectionTestUtils.setField(controller, "localEmbeddingDimension", 3);
        user = new UserPrincipal(
                USER_ID,
                "user@example.com",
                "password",
                TENANT_ID,
                10,
                20,
                4,
                "EMPLOYEE",
                0,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );

        when(chatMessageRepository.countTodayMessagesByUser(eq(TENANT_ID), eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(rateLimiterService.checkLimit(TENANT_ID, USER_ID))
                .thenReturn(new RateLimiterService.RateLimitResult(true, 4, 0));
        when(embeddingService.getDimension(TENANT_ID)).thenReturn(3);
        when(embeddingService.getProvider(TENANT_ID)).thenReturn("GEMINI");
        when(embeddingService.createEmbedding(TENANT_ID, "question")).thenReturn(new float[]{1f, 0f, 0f});
        when(embeddingService.toVectorString(new float[]{1f, 0f, 0f})).thenReturn("[1,0,0]");
        when(documentAccessPolicy.isOrgWideViewer(user)).thenReturn(false);
    }

    @Test
    void strictWithWeakVisibleSourcesReturnsRestrictedResponseWithoutCallingGemini() {
        givenMode("STRICT");
        givenRetrievedChunk(weakChunk());
        givenRestrictedPersistence();
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document()));
        when(embeddingService.parseVector("[0,1,0]")).thenReturn(new float[]{0f, 1f, 0f});
        when(embeddingService.cosineDistance(new float[]{1f, 0f, 0f}, new float[]{0f, 1f, 0f})).thenReturn(0.5);

        ResponseEntity<ChatResponse> response = controller.chat(new ChatRequest("question", null, 7, null, null, null), user);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSources()).isEmpty();
        assertThat(response.getBody().getAnswer()).contains("không tìm thấy thông tin liên quan");
        verify(geminiChatService, never()).generateAnswer(any(), any(), any(), any());
    }

    @Test
    void balancedDocumentQuestionCallsGeminiWithSources() {
        givenMode("BALANCED");
        givenRetrievedChunk(strongChunk());
        givenSuccessfulPersistence();
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document()));
        when(embeddingService.parseVector("[1,0,0]")).thenReturn(new float[]{1f, 0f, 0f});
        when(embeddingService.cosineDistance(new float[]{1f, 0f, 0f}, new float[]{1f, 0f, 0f})).thenReturn(0.0);
        when(geminiChatService.generateAnswer(any(), eq("question"), eq(List.of()), any()))
                .thenReturn(new GeminiChatService.AnswerWithTokens("answer from gemini", 12));

        ResponseEntity<ChatResponse> response = controller.chat(new ChatRequest("question", null, 7, null, null, null), user);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAnswer()).isEqualTo("answer from gemini");
        assertThat(response.getBody().getSources()).hasSize(1);
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(geminiChatService).generateAnswer(context.capture(), eq("question"), eq(List.of()), any());
        assertThat(context.getValue()).contains("Relevant policy content");
    }

    @Test
    void flexibleNoDocumentStillCallsGemini() {
        givenMode("FLEXIBLE");
        givenRetrievedChunk();
        givenSuccessfulPersistence();
        when(geminiChatService.generateAnswer(eq(""), eq("question"), eq(List.of()), any()))
                .thenReturn(new GeminiChatService.AnswerWithTokens("flexible answer", 5));

        ResponseEntity<ChatResponse> response = controller.chat(new ChatRequest("question", null, 7, null, null, null), user);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAnswer()).isEqualTo("flexible answer");
        assertThat(response.getBody().getSources()).isEmpty();
        verify(geminiChatService).generateAnswer(eq(""), eq("question"), eq(List.of()), any());
    }

    private void givenMode(String mode) {
        ChatbotConfig config = new ChatbotConfig();
        config.setTenantId(TENANT_ID);
        config.setMode(mode);
        config.setMaxMessageLength(500);
        config.setMaxMessagesPerDay(100);
        config.setTopK(7);
        config.setSimilarityThreshold(0.7);
        when(chatbotConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));
    }

    private void givenRetrievedChunk(DocumentChunkEntity... chunks) {
        when(chunkRepository.findSimilarChunksWithAccessControl(
                eq(TENANT_ID),
                eq(USER_ID),
                eq(4),
                anyBoolean(),
                eq("[1,0,0]"),
                eq(10),
                eq(20),
                eq(null),
                eq(null),
                anyDouble(),
                anyInt()
        )).thenReturn(List.of(chunks));
    }

    private void givenRestrictedPersistence() {
        ChatSession session = ChatSession.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).userId(USER_ID).build();
        ChatMessage assistant = ChatMessage.builder().id(UUID.randomUUID()).content("restricted").build();
        when(chatHistoryService.getOrCreateSession(null, TENANT_ID, USER_ID, "question")).thenReturn(session);
        when(chatHistoryService.saveAssistantMessage(eq(session.getId()), eq(TENANT_ID), eq(USER_ID), any(), eq(List.of()), eq(0)))
                .thenReturn(assistant);
    }

    private void givenSuccessfulPersistence() {
        ChatSession session = ChatSession.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).userId(USER_ID).build();
        ChatMessage assistant = ChatMessage.builder().id(UUID.randomUUID()).content("answer").build();
        when(chatHistoryService.getOrCreateSession(null, TENANT_ID, USER_ID, "question")).thenReturn(session);
        when(chatHistoryService.saveAssistantMessage(eq(session.getId()), eq(TENANT_ID), eq(USER_ID), any(), any(), anyInt()))
                .thenReturn(assistant);
    }

    private DocumentChunkEntity weakChunk() {
        return chunk("[0,1,0]");
    }

    private DocumentChunkEntity strongChunk() {
        return chunk("[1,0,0]");
    }

    private DocumentChunkEntity chunk(String embedding) {
        return DocumentChunkEntity.builder()
                .id(UUID.randomUUID())
                .documentId(DOCUMENT_ID)
                .tenantId(TENANT_ID)
                .chunkIndex(0)
                .content("Relevant policy content for the user's document question.")
                .embedding(embedding)
                .build();
    }

    private DocumentEntity document() {
        DocumentEntity document = new DocumentEntity();
        document.setId(DOCUMENT_ID);
        document.setTenantId(TENANT_ID);
        document.setDocumentTitle("Policy");
        document.setOriginalFileName("policy.pdf");
        document.setFileName("policy.pdf");
        document.setIsActive(true);
        return document;
    }
}

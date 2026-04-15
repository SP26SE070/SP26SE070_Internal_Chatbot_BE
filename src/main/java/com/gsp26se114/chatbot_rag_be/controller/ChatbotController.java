package com.gsp26se114.chatbot_rag_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsp26se114.chatbot_rag_be.entity.ChatbotConfig;
import com.gsp26se114.chatbot_rag_be.entity.ChatMessage;
import com.gsp26se114.chatbot_rag_be.entity.ChatSession;
import com.gsp26se114.chatbot_rag_be.entity.DocumentChunkEntity;
import com.gsp26se114.chatbot_rag_be.entity.DocumentEntity;
import com.gsp26se114.chatbot_rag_be.payload.request.ChatRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.RateMessageRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.ChatHistoryResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.ChatMessageResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.ChatResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.ConversationSummaryResponse;
import com.gsp26se114.chatbot_rag_be.repository.ChatbotConfigRepository;
import com.gsp26se114.chatbot_rag_be.repository.ChatMessageRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentChunkRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.ChatHistoryService;
import com.gsp26se114.chatbot_rag_be.service.EmbeddingService;
import com.gsp26se114.chatbot_rag_be.service.GeminiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "21. 🤖 Chatbot", description = "RAG-powered chatbot APIs")
public class ChatbotController {

    private final EmbeddingService embeddingService;
    private final GeminiChatService geminiChatService;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final ChatHistoryService chatHistoryService;
    private final ObjectMapper objectMapper;
    private final ChatbotConfigRepository chatbotConfigRepository;
    private final ChatMessageRepository chatMessageRepository;

    @PostMapping("/chat")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chat with RAG-powered bot", description = "Tất cả user trong tenant đều có thể dùng chatbot")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("User {} asking: {}", userDetails.getEmail(), request.getMessage());

            // Load chatbot config once
            ChatbotConfig chatbotConfig = chatbotConfigRepository
                    .findByTenantId(userDetails.getTenantId()).orElse(null);

            // Validate message length
            int maxLength = (chatbotConfig != null && chatbotConfig.getMaxMessageLength() != null)
                    ? chatbotConfig.getMaxMessageLength() : 500;
            if (request.getMessage().length() > maxLength) {
                return ResponseEntity.badRequest().body(
                        ChatResponse.builder()
                                .answer("Tin nhắn quá dài. Vui lòng giới hạn trong " + maxLength + " ký tự.")
                                .conversationId(request.getConversationId())
                                .sources(List.of())
                                .responseTimeMs(0L)
                                .build()
                );
            }

            // Validate daily message limit
            int maxPerDay = (chatbotConfig != null && chatbotConfig.getMaxMessagesPerDay() != null)
                    ? chatbotConfig.getMaxMessagesPerDay() : 100;
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            Long todayCount = chatMessageRepository.countTodayMessagesByUser(
                    userDetails.getTenantId(),
                    userDetails.getId(),
                    startOfDay
            );
            if (todayCount >= maxPerDay) {
                return ResponseEntity.status(429).body(
                        ChatResponse.builder()
                                .answer("Bạn đã đạt giới hạn " + maxPerDay + " tin nhắn hôm nay. Vui lòng thử lại vào ngày mai.")
                                .conversationId(request.getConversationId())
                                .sources(List.of())
                                .responseTimeMs(0L)
                                .build()
                );
            }

            // Step 1 & 2: Create embedding and find similar chunks with access control
            // If RAG pipeline fails, fall back to general knowledge (no context)
            float[] queryEmbedding = null;
            List<DocumentChunkEntity> similarChunks;

            try {
                queryEmbedding = embeddingService.createEmbedding(request.getMessage());
                String vectorString = embeddingService.toVectorString(queryEmbedding);
                log.debug("Query embedding created: {} dimensions", queryEmbedding.length);

                int topK = request.getTopK() != null ? request.getTopK() : 3;
                double maxDistance = 0.7;
                String tagIdsJson = null;

                if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
                    tagIdsJson = objectMapper.writeValueAsString(request.getTagIds().stream().distinct().toList());
                }

                log.info("[DEBUG] tenantId={}, userId={}, deptId={}, roleId={}",
                        userDetails.getTenantId(), userDetails.getId(),
                        userDetails.getDepartmentId(), userDetails.getRoleId());

                similarChunks = chunkRepository.findSimilarChunksWithAccessControl(
                        userDetails.getTenantId(),
                        userDetails.getId(),
                        vectorString,
                        userDetails.getDepartmentId(),
                        userDetails.getRoleId(),
                        request.getCategoryId(),
                        tagIdsJson,
                        maxDistance,
                        topK
                );

                log.info("Found {} similar chunks (similarity threshold: > 30%)", similarChunks.size());

            } catch (Exception ragError) {
                log.warn("RAG pipeline failed, falling back to general knowledge: {}", ragError.getMessage());
                queryEmbedding = null;
                similarChunks = List.of();
            }

            // Step 3: Build context from chunks (if any)
            String context;
            List<ChatResponse.SourceDocument> sources;

            if (similarChunks.isEmpty()) {
                context = "";
                sources = List.of();
            } else {
                final float[] queryEmbeddingFinal = queryEmbedding;

                context = similarChunks.stream()
                        .map(DocumentChunkEntity::getContent)
                        .distinct()
                        .collect(Collectors.joining("\n\n---\n\n"));
                log.info("Context built: {} characters from {} unique chunks",
                         context.length(),
                         similarChunks.stream().map(DocumentChunkEntity::getContent).distinct().count());

                sources = similarChunks.stream()
                        .map(chunk -> {
                            DocumentEntity doc = documentRepository.findById(chunk.getDocumentId()).orElse(null);
                            float[] chunkEmbedding = embeddingService.parseVector(chunk.getEmbedding());
                            double distance = queryEmbeddingFinal != null
                                    ? embeddingService.cosineDistance(queryEmbeddingFinal, chunkEmbedding)
                                    : 1.0;
                            double similarity = Math.round((1.0 - distance) * 100.0) / 100.0;

                            return ChatResponse.SourceDocument.builder()
                                    .documentId(chunk.getDocumentId().toString())
                                    .fileName(doc != null ? doc.getOriginalFileName() : "Unknown")
                                    .chunkContent(chunk.getContent().substring(0, Math.min(200, chunk.getContent().length())) + "...")
                                    .chunkIndex(chunk.getChunkIndex())
                                    .relevanceScore(similarity)
                                    .build();
                        })
                        .filter(source -> source.getRelevanceScore() >= 0.80)
                        .collect(Collectors.toMap(
                                ChatResponse.SourceDocument::getChunkContent,
                                source -> source,
                                (existing, replacement) -> existing.getRelevanceScore() >= replacement.getRelevanceScore()
                                        ? existing : replacement
                        ))
                        .values()
                        .stream()
                        .sorted((s1, s2) -> Double.compare(s2.getRelevanceScore(), s1.getRelevanceScore()))
                        .toList();

                log.info("Filtered to {} unique highly relevant sources (>=80% similarity)", sources.size());
            }

            // Step 4: Generate answer with Gemini
            String answer = geminiChatService.generateAnswer(context, request.getMessage());
            log.info("Answer generated: {} characters", answer.length());

            // Step 5: Persist conversation
            UUID conversationId = null;
            UUID assistantMessageId = null;
            try {
                UUID existingSessionId = parseConversationId(request.getConversationId());
                ChatSession session = chatHistoryService.getOrCreateSession(
                        existingSessionId,
                        userDetails.getTenantId(),
                        userDetails.getId(),
                        request.getMessage()
                );
                conversationId = session.getId();

                // Save user message
                List<Object> sourceChunksForUser = sources.stream()
                        .map(s -> (Object) s)
                        .collect(Collectors.toList());
                chatHistoryService.saveUserMessage(
                        session.getId(),
                        userDetails.getTenantId(),
                        userDetails.getId(),
                        request.getMessage(),
                        sourceChunksForUser
                );

                // Save assistant response
                ChatMessage savedAssistant = chatHistoryService.saveAssistantMessage(
                        session.getId(),
                        userDetails.getTenantId(),
                        userDetails.getId(),
                        answer,
                        sourceChunksForUser,
                        0 // tokens tracking not available from GeminiChatService
                );
                assistantMessageId = savedAssistant.getId();

                // Update session counters
                chatHistoryService.updateSessionCounters(session.getId(), 0);

            } catch (Exception persistError) {
                log.error("Failed to persist chat history, continuing with response", persistError);
                // Non-fatal: still return the answer
            }

            // Step 6: Build response
            ChatResponse response = ChatResponse.builder()
                    .answer(answer)
                    .messageId(assistantMessageId)
                    .conversationId(conversationId != null ? conversationId.toString() : null)
                    .sources(sources)
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to process chat request", e);
            return ResponseEntity.internalServerError().body(
                    ChatResponse.builder()
                            .answer("I apologize, but I encountered an error processing your request: " + e.getMessage())
                            .conversationId(request.getConversationId())
                            .sources(List.of())
                            .responseTimeMs(System.currentTimeMillis() - startTime)
                            .build()
            );
        }
    }

    private ResponseEntity<ChatResponse> handleAndSaveRestrictedResponse(
            ChatRequest request, UserPrincipal userDetails, long startTime, String reason) {

        String answer;
        if ("no_match".equals(reason)) {
            answer = "Xin lỗi, tôi không tìm thấy thông tin liên quan đến câu hỏi của bạn trong tài liệu nội bộ.";
        } else {
            answer = "Xin lỗi, bạn không có quyền truy cập các tài liệu liên quan đến câu hỏi này. " +
                     "Tài liệu này có thể chỉ dành cho các phòng ban hoặc vai trò cụ thể khác. " +
                     "Vui lòng liên hệ quản trị viên nếu bạn cần quyền truy cập.";
        }

        UUID conversationId = null;
        UUID assistantMessageId = null;
        try {
            UUID existingSessionId = parseConversationId(request.getConversationId());
            ChatSession session = chatHistoryService.getOrCreateSession(
                    existingSessionId,
                    userDetails.getTenantId(),
                    userDetails.getId(),
                    request.getMessage()
            );
            conversationId = session.getId();

            chatHistoryService.saveUserMessage(
                    session.getId(),
                    userDetails.getTenantId(),
                    userDetails.getId(),
                    request.getMessage(),
                    List.of()
            );

            ChatMessage savedAssistant = chatHistoryService.saveAssistantMessage(
                    session.getId(),
                    userDetails.getTenantId(),
                    userDetails.getId(),
                    answer,
                    List.of(),
                    0
            );
            assistantMessageId = savedAssistant.getId();

            chatHistoryService.updateSessionCounters(session.getId(), 0);

        } catch (Exception persistError) {
            log.error("Failed to persist restricted response", persistError);
        }

        ChatResponse response = ChatResponse.builder()
                .answer(answer)
                .messageId(assistantMessageId)
                .conversationId(conversationId != null ? conversationId.toString() : null)
                .sources(List.of())
                .responseTimeMs(System.currentTimeMillis() - startTime)
                .build();

        return ResponseEntity.ok(response);
    }

    private UUID parseConversationId(String conversationIdStr) {
        if (conversationIdStr == null || conversationIdStr.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(conversationIdStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List chat conversations", description = "User sees own conversations; admin sees all tenant conversations")
    public ResponseEntity<Page<ConversationSummaryResponse>> getHistory(
            @AuthenticationPrincipal UserPrincipal userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ConversationSummaryResponse> conversations =
                chatHistoryService.getConversations(userDetails, page, size);
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/history/{conversationId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get full conversation with messages", description = "User sees own conversations; admin sees any in tenant")
    public ResponseEntity<ChatHistoryResponse> getConversation(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        ChatHistoryResponse conversation = chatHistoryService.getConversation(conversationId, userDetails);
        return ResponseEntity.ok(conversation);
    }

    @PutMapping("/history/{conversationId}/end")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "End a conversation", description = "Mark a conversation as ENDED")
    public ResponseEntity<ConversationSummaryResponse> endConversation(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        ConversationSummaryResponse result = chatHistoryService.endConversation(conversationId, userDetails);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/messages/{messageId}/rate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Rate an assistant message", description = "Rate a chatbot response (1-5 stars) with optional feedback")
    public ResponseEntity<ChatMessageResponse> rateMessage(
            @PathVariable UUID messageId,
            @Valid @RequestBody RateMessageRequest request,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        ChatMessageResponse result = chatHistoryService.rateMessage(messageId, request, userDetails);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Chatbot service is running");
    }
}

package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.ChatMessage;
import com.gsp26se114.chatbot_rag_be.entity.ChatSession;
import com.gsp26se114.chatbot_rag_be.exception.ForbiddenException;
import com.gsp26se114.chatbot_rag_be.exception.ResourceNotFoundException;
import com.gsp26se114.chatbot_rag_be.payload.request.RateMessageRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.ChatHistoryResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.ChatMessageResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.ConversationSummaryResponse;
import com.gsp26se114.chatbot_rag_be.repository.ChatMessageRepository;
import com.gsp26se114.chatbot_rag_be.repository.ChatSessionRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /**
     * Save a user message and return the stored ChatMessage.
     */
    @Transactional
    public ChatMessage saveUserMessage(UUID sessionId, UUID tenantId, UUID userId,
                                        String content, List<Object> sourceChunks) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .tenantId(tenantId)
                .userId(userId)
                .role("USER")
                .content(content)
                .sourceChunks(sourceChunks)
                .build();
        return messageRepository.save(message);
    }

    /**
     * Save an assistant (bot) message and return the stored ChatMessage.
     */
    @Transactional
    public ChatMessage saveAssistantMessage(UUID sessionId, UUID tenantId, UUID userId,
                                             String content, List<Object> sourceChunks,
                                             Integer tokensUsed) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .tenantId(tenantId)
                .userId(userId)
                .role("ASSISTANT")
                .content(content)
                .sourceChunks(sourceChunks)
                .tokensUsed(tokensUsed)
                .build();
        return messageRepository.save(message);
    }

    /**
     * Get or create a ChatSession. If existingSessionId is null, create a new one.
     */
    @Transactional
    public ChatSession getOrCreateSession(UUID existingSessionId, UUID tenantId, UUID userId,
                                           String firstMessage) {
        if (existingSessionId != null) {
            return sessionRepository.findById(existingSessionId)
                    .filter(s -> s.getUserId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        }

        ChatSession session = ChatSession.builder()
                .tenantId(tenantId)
                .userId(userId)
                .title(truncate(firstMessage, 100))
                .status("ACTIVE")
                .startedAt(LocalDateTime.now())
                .lastMessageAt(LocalDateTime.now())
                .totalMessages(0)
                .totalTokensUsed(0)
                .build();
        return sessionRepository.save(session);
    }

    /**
     * Update session counters after a round-trip (user + assistant).
     */
    @Transactional
    public void updateSessionCounters(UUID sessionId, Integer tokensUsed) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setTotalMessages(Integer.sum(
                    Objects.requireNonNullElse(session.getTotalMessages(), 0), 2));
            session.setTotalTokensUsed(Integer.sum(
                    Objects.requireNonNullElse(session.getTotalTokensUsed(), 0),
                    Objects.requireNonNullElse(tokensUsed, 0)));
            session.setLastMessageAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    /**
     * Get paginated conversation list for the current user.
     */
    @Transactional(readOnly = true)
    public Page<ConversationSummaryResponse> getConversations(UserPrincipal user, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<ChatSession> sessions = sessionRepository.findByUserIdOrderByLastMessageAtDesc(user.getId(), pageable);
        return sessions.map(this::toSummary);
    }

    /**
     * Get full conversation with all messages.
     */
    @Transactional(readOnly = true)
    public ChatHistoryResponse getConversation(UUID conversationId, UserPrincipal user) {
        ChatSession session = sessionRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        // Permission check: admin can view any in tenant; user can only view own
        if (!isAdmin(user) && !session.getUserId().equals(user.getId())) {
            throw new ForbiddenException("You do not have access to this conversation");
        }
        if (!session.getTenantId().equals(user.getTenantId())) {
            throw new ForbiddenException("You do not have access to this conversation");
        }

        List<ChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(conversationId);

        return ChatHistoryResponse.builder()
                .conversationId(session.getId())
                .title(session.getTitle())
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .lastMessageAt(session.getLastMessageAt())
                .totalMessages(session.getTotalMessages())
                .totalTokensUsed(session.getTotalTokensUsed())
                .messages(messages.stream().map(this::toMessageResponse).collect(Collectors.toList()))
                .build();
    }

    /**
     * End (mark as ENDED) a conversation.
     */
    @Transactional
    public ConversationSummaryResponse endConversation(UUID conversationId, UserPrincipal user) {
        ChatSession session = sessionRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (!isAdmin(user) && !session.getUserId().equals(user.getId())) {
            throw new ForbiddenException("You do not have access to this conversation");
        }
        if (!session.getTenantId().equals(user.getTenantId())) {
            throw new ForbiddenException("You do not have access to this conversation");
        }

        session.setStatus("ENDED");
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);
        return toSummary(session);
    }

    /**
     * Rate an assistant message.
     */
    @Transactional
    public ChatMessageResponse rateMessage(UUID messageId, RateMessageRequest request, UserPrincipal user) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        if (!message.getTenantId().equals(user.getTenantId())) {
            throw new ForbiddenException("You do not have access to this message");
        }
        if (!"ASSISTANT".equals(message.getRole())) {
            throw new IllegalArgumentException("Only assistant messages can be rated");
        }

        Short rating = request.getRating();
        if (!isBinaryFeedbackRating(rating)) {
            throw new IllegalArgumentException("Rating must be 5 (helpful) or 1 (not-helpful)");
        }

        message.setRating(rating);
        if (request.getFeedbackText() != null) {
            message.setFeedbackText(request.getFeedbackText());
        }
        message.setRatedAt(LocalDateTime.now());

        return toMessageResponse(messageRepository.save(message));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isAdmin(UserPrincipal user) {
        return "TENANT_ADMIN".equals(user.getRoleCode())
            || "SUPER_ADMIN".equals(user.getRoleCode());
    }

    private boolean isBinaryFeedbackRating(Short rating) {
        return rating != null && (rating == 5 || rating == 1);
    }

    private ConversationSummaryResponse toSummary(ChatSession session) {
        return ConversationSummaryResponse.builder()
                .conversationId(session.getId())
                .title(session.getTitle())
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .lastMessageAt(session.getLastMessageAt())
                .totalMessages(session.getTotalMessages())
                .totalTokensUsed(session.getTotalTokensUsed())
                .build();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage msg) {
        return ChatMessageResponse.builder()
                .messageId(msg.getId())
                .conversationId(msg.getSessionId().toString())
                .role(msg.getRole())
                .content(msg.getContent())
                .sourceChunks(msg.getSourceChunks())
                .rating(msg.getRating() != null ? msg.getRating().intValue() : null)
                .feedbackText(msg.getFeedbackText())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}

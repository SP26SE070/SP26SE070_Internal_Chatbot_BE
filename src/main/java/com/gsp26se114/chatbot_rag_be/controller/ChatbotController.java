package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.DocumentChunkEntity;
import com.gsp26se114.chatbot_rag_be.entity.DocumentEntity;
import com.gsp26se114.chatbot_rag_be.payload.request.ChatRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.ChatResponse;
import com.gsp26se114.chatbot_rag_be.repository.DocumentChunkRepository;
import com.gsp26se114.chatbot_rag_be.repository.DocumentRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.EmbeddingService;
import com.gsp26se114.chatbot_rag_be.service.GeminiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for RAG-powered chatbot queries
 */
@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "19. 🤖 Chatbot", description = "RAG-powered chatbot APIs")
public class ChatbotController {

    private final EmbeddingService embeddingService;
    private final GeminiChatService geminiChatService;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;

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

            // Step 1: Create embedding for user query
            float[] queryEmbedding = embeddingService.createEmbedding(request.getMessage());
            String vectorString = embeddingService.toVectorString(queryEmbedding);
            log.debug("Query embedding created: {} dimensions", queryEmbedding.length);

            // Step 2: Find similar chunks with access control
            int topK = request.getTopK() != null ? request.getTopK() : 3;
            double maxDistance = 0.35; // Only retrieve chunks with cosine distance < 0.35 (similarity > 65%)
            
            List<DocumentChunkEntity> similarChunks = chunkRepository.findSimilarChunksWithAccessControl(
                    userDetails.getTenantId(),
                    userDetails.getId(),
                    vectorString,
                    userDetails.getDepartmentId(),
                    userDetails.getRoleId(),
                    maxDistance,
                    topK
            );

            log.info("Found {} similar chunks (similarity threshold: > 65%)", similarChunks.size());

            // Step 3: Build context from chunks (if any)
            String context;
            List<ChatResponse.SourceDocument> sources;
            
            if (similarChunks.isEmpty()) {
                // Check if there are documents in tenant but user has no access
                long totalDocsInTenant = documentRepository.countByTenantIdAndIsActive(
                    userDetails.getTenantId(), true
                );
                
                if (totalDocsInTenant > 0) {
                    // Documents exist but user cannot access them
                    log.warn("User {} tried to access documents outside their permission scope", userDetails.getEmail());
                    
                    ChatResponse restrictedResponse = ChatResponse.builder()
                            .answer("Xin lỗi, bạn không có quyền truy cập các tài liệu liên quan đến câu hỏi này. " +
                                   "Tài liệu này có thể chỉ dành cho các phòng ban hoặc vai trò cụ thể khác. " +
                                   "Vui lòng liên hệ quản trị viên nếu bạn cần quyền truy cập.")
                            .conversationId(request.getConversationId() != null ? request.getConversationId() : UUID.randomUUID().toString())
                            .sources(List.of())
                            .responseTimeMs(System.currentTimeMillis() - startTime)
                            .build();
                    
                    return ResponseEntity.ok(restrictedResponse);
                }
                
                // No documents uploaded - chatbot can still answer using general knowledge
                context = "";
                sources = List.of();
                log.info("No relevant documents found - answering with general knowledge");
            } else {
                // Build context from relevant chunks (deduplicate by content to avoid repetition)
                context = similarChunks.stream()
                        .map(DocumentChunkEntity::getContent)
                        .distinct() // Remove duplicate content
                        .collect(Collectors.joining("\n\n---\n\n"));
                log.info("Context built: {} characters from {} unique chunks - Content preview: {}", 
                         context.length(),
                         similarChunks.stream().map(DocumentChunkEntity::getContent).distinct().count(),
                         context.substring(0, Math.min(300, context.length())) + "...");
                
                // Build source references from relevant chunks
                sources = similarChunks.stream()
                        .map(chunk -> {
                            DocumentEntity doc = documentRepository.findById(chunk.getDocumentId()).orElse(null);
                            // Calculate cosine distance and convert to similarity score
                            float[] chunkEmbedding = embeddingService.parseVector(chunk.getEmbedding());
                            double distance = embeddingService.cosineDistance(queryEmbedding, chunkEmbedding);
                            double similarity = Math.round((1.0 - distance) * 100.0) / 100.0; // Round to 2 decimals
                            
                            return ChatResponse.SourceDocument.builder()
                                    .documentId(chunk.getDocumentId().toString())
                                    .fileName(doc != null ? doc.getOriginalFileName() : "Unknown")
                                    .chunkContent(chunk.getContent().substring(0, Math.min(200, chunk.getContent().length())) + "...")
                                    .chunkIndex(chunk.getChunkIndex())
                                    .relevanceScore(similarity)
                                    .build();
                        })
                        .filter(source -> source.getRelevanceScore() >= 0.80) // Only keep highly relevant sources (≥80% similarity)
                        .collect(Collectors.toMap(
                                ChatResponse.SourceDocument::getChunkContent, // Key: chunk content
                                source -> source,                              // Value: source itself
                                (existing, replacement) -> existing.getRelevanceScore() >= replacement.getRelevanceScore() 
                                        ? existing : replacement               // Keep higher score if duplicate
                        ))
                        .values()
                        .stream()
                        .sorted((s1, s2) -> Double.compare(s2.getRelevanceScore(), s1.getRelevanceScore())) // Sort by score descending
                        .toList();
                
                log.info("Filtered to {} unique highly relevant sources (≥80% similarity)", sources.size());
            }

            // Step 4: Generate answer with Gemini (works with or without context)
            String answer = geminiChatService.generateAnswer(context, request.getMessage());
            log.info("Answer generated: {} characters", answer.length());

            // Step 5: Build response
            ChatResponse response = ChatResponse.builder()
                    .answer(answer)
                    .conversationId(request.getConversationId() != null ? request.getConversationId() : UUID.randomUUID().toString())
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

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Chatbot service is running");
    }
}

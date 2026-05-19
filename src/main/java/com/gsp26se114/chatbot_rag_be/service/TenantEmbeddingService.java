package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.ChatbotConfig;
import com.gsp26se114.chatbot_rag_be.repository.ChatbotConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

/**
 * Resolve embedding provider per tenant based on chatbot config.
 */
@Service
@RequiredArgsConstructor
public class TenantEmbeddingService {

    private final ChatbotConfigRepository chatbotConfigRepository;
    private final GeminiEmbeddingService geminiEmbeddingService;
    private final LocalEmbeddingService localEmbeddingService;

    @Value("${embedding.provider:gemini}")
    private String defaultProvider;

    public float[] createEmbedding(UUID tenantId, String text) {
        return resolveProvider(tenantId).createEmbedding(text);
    }

    public String getModelName(UUID tenantId) {
        return resolveProvider(tenantId).getModelName();
    }

    public int getDimension(UUID tenantId) {
        return resolveProvider(tenantId).getDimension();
    }

    public String getProvider(UUID tenantId) {
        return normalizedProvider(tenantId);
    }

    public String toVectorString(float[] embedding) {
        return geminiEmbeddingService.toVectorString(embedding);
    }

    public float[] parseVector(String vectorString) {
        return geminiEmbeddingService.parseVector(vectorString);
    }

    public double cosineDistance(float[] v1, float[] v2) {
        return geminiEmbeddingService.cosineDistance(v1, v2);
    }

    private EmbeddingService resolveProvider(UUID tenantId) {
        return "LOCAL".equals(normalizedProvider(tenantId))
                ? localEmbeddingService
                : geminiEmbeddingService;
    }

    private String normalizedProvider(UUID tenantId) {
        String configured = chatbotConfigRepository.findByTenantId(tenantId)
                .map(ChatbotConfig::getEmbeddingProvider)
                .orElse(defaultProvider);
        if (configured == null) {
            return "GEMINI";
        }
        String normalized = configured.trim().toUpperCase(Locale.ROOT);
        return "LOCAL".equals(normalized) ? "LOCAL" : "GEMINI";
    }
}

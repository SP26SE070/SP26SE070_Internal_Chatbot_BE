package com.gsp26se114.chatbot_rag_be.config;

import com.gsp26se114.chatbot_rag_be.service.EmbeddingService;
import com.gsp26se114.chatbot_rag_be.service.GeminiEmbeddingService;
import com.gsp26se114.chatbot_rag_be.service.LocalEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Optional startup self-test for embedding provider.
 * Useful in POC/demo to verify provider switch quickly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmbeddingStartupSelfTestRunner implements CommandLineRunner {

    private final GeminiEmbeddingService geminiEmbeddingService;
    private final LocalEmbeddingService localEmbeddingService;

    @Value("${embedding.startup-self-test:false}")
    private boolean enabled;

    @Value("${embedding.startup-self-test-text:Embedding startup self-test}")
    private String testText;

    @Value("${embedding.provider:gemini}")
    private String startupProvider;

    @Override
    public void run(String... args) {
        EmbeddingService embeddingService = resolveStartupProvider();
        if (!enabled) {
            log.info("[EMBEDDING] Startup self-test disabled. Provider={}, model={}, dimension={}",
                    providerName(), embeddingService.getModelName(), embeddingService.getDimension());
            return;
        }
        try {
            float[] vector = embeddingService.createEmbedding(testText);
            log.info("[EMBEDDING] Startup self-test passed. Provider={}, model={}, dimension={}, output={}",
                    providerName(), embeddingService.getModelName(), embeddingService.getDimension(), vector.length);
        } catch (Exception e) {
            log.error("[EMBEDDING] Startup self-test failed (app will still start). Provider={}, model={}. Error={}",
                    providerName(), embeddingService.getModelName(), e.getMessage(), e);
        }
    }

    private String providerName() {
        return resolveStartupProvider().getClass().getSimpleName();
    }

    private EmbeddingService resolveStartupProvider() {
        String normalized = startupProvider == null ? "GEMINI" : startupProvider.trim().toUpperCase(Locale.ROOT);
        return "LOCAL".equals(normalized) ? localEmbeddingService : geminiEmbeddingService;
    }
}

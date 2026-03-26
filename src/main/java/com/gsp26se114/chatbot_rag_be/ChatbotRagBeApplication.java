package com.gsp26se114.chatbot_rag_be;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// Dùng excludeName thay vì exclude để không bị lỗi Import
@SpringBootApplication(excludeName = {
    "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration",
    "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingAutoConfiguration"
})
@EnableScheduling
@EnableAsync  // Enable @Async for background document processing
public class ChatbotRagBeApplication {

    /**
     * Spring Boot does not read {@code .env} by default. Load project-root {@code .env}
     * into system properties so {@code ${GEMINI_API_KEY}} and SePay vars resolve when running from IDE.
     * OS environment variables take precedence over {@code .env}.
     */
    private static void loadDotEnv() {
        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();
        dotenv.entries().forEach(entry -> {
            String key = entry.getKey();
            if (System.getenv(key) != null) {
                return;
            }
            if (System.getProperty(key) != null) {
                return;
            }
            System.setProperty(key, entry.getValue());
        });
    }

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(ChatbotRagBeApplication.class, args);
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("DocProcess-");
        executor.initialize();
        return executor;
    }
}

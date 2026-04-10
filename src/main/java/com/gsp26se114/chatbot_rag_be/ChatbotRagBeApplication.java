package com.gsp26se114.chatbot_rag_be;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

// Dùng excludeName thay vì exclude để không bị lỗi Import
@SpringBootApplication(excludeName = {
    "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration",
    "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingAutoConfiguration"
})
@EnableScheduling
@EnableAsync  // Enable @Async for background document processing
public class ChatbotRagBeApplication {

    /**
     * Spring Boot does not read {@code .env} by default. Load {@code .env}
     * from current working directory or child Maven module directory.
     * OS environment variables take precedence over {@code .env}.
     */
    private static void loadDotEnv() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        List<Path> candidateDirectories = new ArrayList<>();
        candidateDirectories.add(workingDirectory);

        // If app is launched from a workspace root, look for Maven module folders that contain .env.
        try (Stream<Path> children = Files.list(workingDirectory)) {
            children.filter(Files::isDirectory)
                    .filter(dir -> Files.exists(dir.resolve("pom.xml")))
                    .filter(dir -> Files.exists(dir.resolve(".env")))
                    .forEach(candidateDirectories::add);
        } catch (IOException ignored) {
            // Ignore scanning failures and fallback to current working directory only.
        }

        for (Path directory : candidateDirectories) {
            Path envPath = directory.resolve(".env").toAbsolutePath().normalize();
            if (!Files.exists(envPath)) {
                continue;
            }

            Dotenv dotenv = Dotenv.configure()
                    .directory(directory.toString())
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
            return;
        }
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

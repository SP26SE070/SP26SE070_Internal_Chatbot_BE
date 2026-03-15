package com.gsp26se114.chatbot_rag_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Dùng excludeName thay vì exclude để không bị lỗi Import
@SpringBootApplication(excludeName = {
    "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration",        
    "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingAutoConfiguration"
})
@EnableScheduling
@EnableAsync  // Enable @Async for background document processing
public class ChatbotRagBeApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatbotRagBeApplication.class, args);
    }
}
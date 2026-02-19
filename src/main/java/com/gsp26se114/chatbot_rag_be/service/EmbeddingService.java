package com.gsp26se114.chatbot_rag_be.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service để tạo embeddings sử dụng Gemini API trực tiếp
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "spring.ai.google.genai.api-key")
public class EmbeddingService {

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    @Value("${spring.ai.google.genai.embedding.options.model:text-embedding-004}")
    private String embeddingModel;

    @Value("${spring.ai.google.genai.embedding.options.output-dimensionality:768}")
    private int outputDimensionality;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    /**
     * Tạo embedding cho một đoạn text
     * 
     * @param text Text cần embedding
     * @return Vector embedding (outputDimensionality dimensions)
     */
    public float[] createEmbedding(String text) {
        try {
            // Build request body - Gemini API format
            JsonObject requestBody = new JsonObject();
            
            // Add content field
            JsonObject content = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", text);
            parts.add(part);
            content.add("parts", parts);
            requestBody.add("content", content);
            
            // Add outputDimensionality to reduce vector size (Matryoshka support)
            requestBody.addProperty("outputDimensionality", outputDimensionality);

            // API URL - use models/ prefix in path
            String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent?key=%s",
                embeddingModel, apiKey
            );

            // HTTP Request
            RequestBody body = RequestBody.create(
                gson.toJson(requestBody),
                MediaType.get("application/json")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            // Execute
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Gemini API error: " + response.code());
                }

                String responseBody = response.body().string();
                JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                JsonArray values = json.getAsJsonObject("embedding")
                                      .getAsJsonArray("values");

                float[] embedding = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    embedding[i] = values.get(i).getAsFloat();
                }

                log.debug("Created embedding: dimensions={}", embedding.length);
                return embedding;
            }
            
        } catch (IOException e) {
            log.error("Failed to create embedding", e);
            throw new RuntimeException("Embedding creation failed", e);
        }
    }

    /**
     * Tạo embeddings cho nhiều texts (batch)
     */
    public List<float[]> createEmbeddings(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(createEmbedding(text));
        }
        return embeddings;
    }

    /**
     * Convert float[] sang chuỗi PostgreSQL vector format
     * Example: [0.1, 0.2, 0.3] → "[0.1,0.2,0.3]"
     */
    public String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}

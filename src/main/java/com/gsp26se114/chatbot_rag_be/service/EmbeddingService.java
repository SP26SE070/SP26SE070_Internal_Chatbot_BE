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

    @Value("${spring.ai.google.genai.embedding.options.model:gemini-embedding-001}")
    private String embeddingModel;

    @Value("${spring.ai.google.genai.embedding.options.output-dimensionality:768}")
    private int outputDimensionality;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    /**
     * Tạo embedding cho một đoạn text với retry logic
     * 
     * @param text Text cần embedding
     * @return Vector embedding (outputDimensionality dimensions)
     */
    public float[] createEmbedding(String text) {
        int retries = 0;
        Exception lastException = null;

        while (retries < MAX_RETRIES) {
            try {
                // Add small delay between retries to avoid rate limiting
                if (retries > 0) {
                    Thread.sleep(RETRY_DELAY_MS * retries);
                    log.info("Retrying embedding creation (attempt {}/{})", retries + 1, MAX_RETRIES);
                }

                return createEmbeddingInternal(text);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Embedding creation interrupted", e);
            } catch (Exception e) {
                lastException = e;
                retries++;
                log.warn("Embedding attempt {}/{} failed: {}", retries, MAX_RETRIES, e.getMessage());
            }
        }

        log.error("Failed to create embedding after {} retries", MAX_RETRIES, lastException);
        throw new RuntimeException("Embedding creation failed after " + MAX_RETRIES + " retries. " +
                "Error: " + (lastException != null ? lastException.getMessage() : "Unknown"), lastException);
    }

    /**
     * Internal method để tạo embedding (không có retry logic)
     */
    private float[] createEmbeddingInternal(String text) throws IOException {
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

        log.debug("Calling Gemini Embedding API: model={}, dimensions={}", embeddingModel, outputDimensionality);

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
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                log.error("Gemini API error: code={}, body={}", response.code(), responseBody);
                
                // Parse error details if available
                String errorMessage = "Gemini API error: " + response.code();
                try {
                    JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
                    if (errorJson.has("error")) {
                        JsonObject error = errorJson.getAsJsonObject("error");
                        String message = error.has("message") ? error.get("message").getAsString() : "";
                        errorMessage += " - " + message;
                        
                        // Add helpful hints based on error code
                        if (response.code() == 403) {
                            errorMessage += "\nKiểm tra: 1) API key hợp lệ, 2) Enable Generative Language API tại Google Cloud Console, 3) Quota còn";
                        } else if (response.code() == 429) {
                            errorMessage += "\nRate limit exceeded. Đợi một chút và thử lại.";
                        } else if (response.code() == 400) {
                            errorMessage += "\nKiểm tra model name và request format.";
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not parse error response", e);
                }
                
                throw new RuntimeException(errorMessage);
            }

            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            
            if (!json.has("embedding") || !json.getAsJsonObject("embedding").has("values")) {
                log.error("Invalid response format: {}", responseBody);
                throw new RuntimeException("Invalid embedding response format");
            }
            
            JsonArray values = json.getAsJsonObject("embedding")
                                  .getAsJsonArray("values");

            float[] embedding = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                embedding[i] = values.get(i).getAsFloat();
            }

            log.debug("Created embedding: dimensions={}", embedding.length);
            return embedding;
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

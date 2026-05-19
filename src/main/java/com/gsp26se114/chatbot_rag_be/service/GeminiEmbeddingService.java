package com.gsp26se114.chatbot_rag_be.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Default cloud embedding provider using Gemini API.
 */
@Service
@Slf4j
public class GeminiEmbeddingService implements EmbeddingService {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 30_000L;

    @Value("${spring.ai.google.genai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.google.genai.embedding.options.model:gemini-embedding-001}")
    private String embeddingModel;

    @Value("${spring.ai.google.genai.embedding.options.output-dimensionality:768}")
    private int outputDimensionality;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();

    @Override
    public float[] createEmbedding(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("GEMINI_API_KEY is missing. Please configure it in environment or .env");
        }

        int retries = 0;
        Exception lastException = null;

        while (retries < MAX_RETRIES) {
            try {
                if (retries > 0) {
                    Thread.sleep(RETRY_DELAY_MS * retries);
                    log.info("Retrying Gemini embedding (attempt {}/{})", retries + 1, MAX_RETRIES);
                }
                return createEmbeddingInternal(text);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Embedding creation interrupted", e);
            } catch (Exception e) {
                lastException = e;
                retries++;
                log.warn("Gemini embedding attempt {}/{} failed: {}", retries, MAX_RETRIES, e.getMessage());
            }
        }

        throw new RuntimeException(
                "Gemini embedding failed after " + MAX_RETRIES + " retries. Error: "
                        + (lastException != null ? lastException.getMessage() : "Unknown"),
                lastException
        );
    }

    @Override
    public String getModelName() {
        return embeddingModel;
    }

    @Override
    public int getDimension() {
        return outputDimensionality;
    }

    private float[] createEmbeddingInternal(String text) throws IOException {
        JsonObject requestBody = new JsonObject();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        parts.add(part);
        content.add("parts", parts);
        requestBody.add("content", content);
        requestBody.addProperty("outputDimensionality", outputDimensionality);

        String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent?key=%s",
                embeddingModel, apiKey
        );

        RequestBody body = RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json"));
        Request request = new Request.Builder().url(url).post(body).build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("Gemini API error: " + response.code() + " body=" + responseBody);
            }
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            if (!json.has("embedding") || !json.getAsJsonObject("embedding").has("values")) {
                throw new RuntimeException("Invalid Gemini embedding response");
            }
            JsonArray values = json.getAsJsonObject("embedding").getAsJsonArray("values");
            float[] embedding = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                embedding[i] = values.get(i).getAsFloat();
            }
            return embedding;
        }
    }
}

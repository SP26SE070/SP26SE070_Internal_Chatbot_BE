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
 * Embedding provider using ChiaseGPU (OpenAI-compatible) endpoint.
 * Calls gemini-embedding-001 via ChiaseGPU which returns 3072-dim vectors.
 */
@Service
@Slf4j
public class GeminiEmbeddingService implements EmbeddingService {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5_000L;

    @Value("${chiasegpu.api-key:}")
    private String apiKey;

    @Value("${chiasegpu.base-url:https://llm.chiasegpu.vn/v1}")
    private String baseUrl;

    @Value("${chiasegpu.models.embedding:tse/gemini/gemini-embedding-001}")
    private String embeddingModel;

    @Value("${chiasegpu.embedding-dimension:3072}")
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
            throw new RuntimeException("CHIASEGPU_API_KEY is missing. Please configure it in environment or .env");
        }

        int retries = 0;
        Exception lastException = null;

        while (retries < MAX_RETRIES) {
            try {
                if (retries > 0) {
                    Thread.sleep(RETRY_DELAY_MS * retries);
                    log.info("Retrying embedding (attempt {}/{})", retries + 1, MAX_RETRIES);
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

        throw new RuntimeException(
                "Embedding failed after " + MAX_RETRIES + " retries. Error: "
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
        requestBody.addProperty("model", embeddingModel);
        requestBody.addProperty("input", text);

        String url = baseUrl + "/embeddings";

        RequestBody body = RequestBody.create(
                gson.toJson(requestBody),
                MediaType.get("application/json; charset=utf-8")
        );
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("ChiaseGPU embedding error: " + response.code() + " body=" + responseBody);
            }
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            // OpenAI-compatible response: { "data": [ { "embedding": [...] } ] }
            JsonArray data = json.getAsJsonArray("data");
            if (data == null || data.isEmpty()) {
                throw new RuntimeException("Invalid ChiaseGPU embedding response: no data array");
            }
            JsonObject first = data.get(0).getAsJsonObject();
            JsonArray values = first.getAsJsonArray("embedding");
            if (values == null || values.isEmpty()) {
                throw new RuntimeException("Invalid ChiaseGPU embedding response: no embedding array");
            }

            float[] embedding = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                embedding[i] = values.get(i).getAsFloat();
            }
            return embedding;
        }
    }
}

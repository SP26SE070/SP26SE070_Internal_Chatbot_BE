package com.gsp26se114.chatbot_rag_be.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Local embedding provider for on-premise mode using BGE-M3.
 *
 * Supports local HTTP embedding servers:
 * - Ollama (current): POST /api/embed -> JSON { "model", "input" } -> { "embeddings": [[...]] }
 * - Ollama legacy: POST /api/embeddings -> { "model", "prompt" } -> { "embedding": [...] } (often empty; avoid)
 * - OpenAI-compatible: POST /v1/embeddings -> { "data":[{"embedding":[...]}] }
 */
@Service
@Slf4j
public class LocalEmbeddingService implements EmbeddingService {

    @Value("${embedding.local.model-name:bge-m3}")
    private String modelName;

    @Value("${embedding.local.fallback-models:}")
    private String fallbackModelsRaw;

    @Value("${embedding.local.dimension:1024}")
    private int dimension;

    @Value("${embedding.local.endpoint:http://localhost:11434/api/embed}")
    private String endpoint;

    @Value("${embedding.local.api-key:}")
    private String apiKey;

    @Value("${embedding.local.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${embedding.local.max-chars:6000}")
    private int maxChars;

    private final Gson gson = new Gson();

    @Override
    public float[] createEmbedding(String text) {
        String safeText = sanitizeText(text);
        if (safeText.isBlank()) {
            throw new IllegalArgumentException("Embedding input is empty after sanitization");
        }
        String callUrl = normalizeOllamaEndpoint(endpoint);
        OkHttpClient httpClient = buildHttpClient();

        List<String> candidates = buildRetryCandidates(safeText);
        List<String> modelCandidates = buildModelCandidates();
        RuntimeException lastException = null;

        for (String model : modelCandidates) {
            for (int attempt = 0; attempt < candidates.size(); attempt++) {
                String candidate = candidates.get(attempt);
                try {
                    float[] embedding = requestEmbedding(httpClient, callUrl, candidate, model);
                    if (embedding.length != dimension) {
                        throw new IllegalArgumentException(
                                "Embedding dimension mismatch: expected " + dimension + " but got " + embedding.length
                                        + ". Check model and embedding.local.dimension config."
                        );
                    }
                    if (attempt > 0 || !model.equals(modelName)) {
                        log.warn("Local embedding succeeded after retry {} using model {} (len={})",
                                attempt + 1, model, candidate.length());
                    }
                    return embedding;
                } catch (RuntimeException e) {
                    lastException = e;
                    if (!isRetryableEmbeddingError(e)) {
                        throw e;
                    }
                    if (attempt == candidates.size() - 1) {
                        break;
                    }
                    log.warn("Local embedding attempt {} failed (model={}, len={}): {}. Retrying with safer input.",
                            attempt + 1, model, candidate.length(), e.getMessage());
                }
            }

            if (!model.equals(modelName) || modelCandidates.size() == 1) {
                continue;
            }

            if (modelCandidates.size() > 1) {
                log.warn("Local embedding failed for model {}. Trying fallback models: {}",
                        modelName, modelCandidates.subList(1, modelCandidates.size()));
            }
        }

        throw lastException != null ? lastException : new RuntimeException("Local embedding failed");
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    private static String normalizeOllamaEndpoint(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        if (raw.contains("/api/embeddings")) {
            String fixed = raw.replace("/api/embeddings", "/api/embed");
            log.warn("Ollama /api/embeddings is deprecated and often returns empty vectors; using {} instead", fixed);
            return fixed;
        }
        return raw;
    }

    private JsonObject buildRequestPayload(String callUrl, String safeText, String modelOverride) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", modelOverride != null && !modelOverride.isBlank() ? modelOverride : modelName);
        String ep = callUrl == null ? "" : callUrl.toLowerCase();
        if (ep.contains("/v1/embeddings")) {
            payload.addProperty("input", safeText);
        } else if (ep.contains("/api/embeddings")) {
            payload.addProperty("prompt", safeText);
        } else {
            payload.addProperty("input", safeText);
        }
        return payload;
    }

    private OkHttpClient buildHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    private float[] requestEmbedding(OkHttpClient httpClient, String callUrl, String safeText, String modelOverride) {
        JsonObject payload = buildRequestPayload(callUrl, safeText, modelOverride);
        Request.Builder requestBuilder = new Request.Builder()
                .url(callUrl)
                .post(RequestBody.create(gson.toJson(payload), MediaType.get("application/json")));
        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("Local embedding server error: " + response.code() + " body=" + responseBody);
            }
            return parseEmbeddingResponse(responseBody);
        } catch (IOException e) {
            throw new RuntimeException("Cannot connect to local embedding server at " + callUrl + ": " + e.getMessage(), e);
        }
    }

    private List<String> buildModelCandidates() {
        List<String> models = new ArrayList<>();
        if (modelName != null && !modelName.isBlank()) {
            models.add(modelName.trim());
        }
        for (String fallback : parseFallbackModels()) {
            if (!models.contains(fallback)) {
                models.add(fallback);
            }
        }
        return models;
    }

    private List<String> parseFallbackModels() {
        if (fallbackModelsRaw == null || fallbackModelsRaw.isBlank()) {
            return List.of();
        }
        List<String> models = new ArrayList<>();
        for (String part : fallbackModelsRaw.split(",")) {
            String trimmed = part == null ? "" : part.trim();
            if (!trimmed.isBlank()) {
                models.add(trimmed);
            }
        }
        return models;
    }

    private float[] parseEmbeddingResponse(String body) {
        if (body == null || body.isBlank()) {
            throw new RuntimeException("Empty embedding response from local server");
        }
        final JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON from local embedding server: "
                    + body.substring(0, Math.min(400, body.length())), e);
        }
        if (!root.isJsonObject()) {
            throw new RuntimeException("Embedding response is not a JSON object");
        }
        JsonObject json = root.getAsJsonObject();
        JsonArray values = null;

        if (json.has("embeddings") && json.get("embeddings").isJsonArray()) {
            JsonArray outer = json.getAsJsonArray("embeddings");
            if (!outer.isEmpty() && outer.get(0).isJsonArray()) {
                values = outer.get(0).getAsJsonArray();
            }
        }

        if (values == null && json.has("embedding") && json.get("embedding").isJsonArray()) {
            values = json.getAsJsonArray("embedding");
        }

        if (values != null && values.size() == 0) {
            throw new RuntimeException(
                    "Local embedding returned an empty vector. If you use Ollama, set embedding.local.endpoint "
                            + "to http://localhost:11434/api/embed (POST /api/embeddings is deprecated and often returns [])."
            );
        }

        if (values == null && json.has("data") && json.get("data").isJsonArray()) {
            JsonArray data = json.getAsJsonArray("data");
            if (!data.isEmpty() && data.get(0).isJsonObject()) {
                JsonObject first = data.get(0).getAsJsonObject();
                if (first.has("embedding") && first.get("embedding").isJsonArray()) {
                    values = first.getAsJsonArray("embedding");
                }
            }
        }

        if (values == null) {
            throw new RuntimeException("Invalid embedding response from local server: missing embeddings/embedding array");
        }

        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            float value = values.get(i).getAsFloat();
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Local embedding returned non-finite value");
            }
            vector[i] = value;
        }
        return vector;
    }

    private boolean isRetryableEmbeddingError(RuntimeException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("cannot connect to local embedding server")) {
            return false;
        }
        if (lower.contains("unauthorized") || lower.contains("forbidden")) {
            return false;
        }
        return lower.contains("nan")
                || lower.contains("non-finite")
                || lower.contains("invalid json")
                || lower.contains("empty embedding response")
                || lower.contains("invalid embedding response")
                || lower.contains("local embedding server error");
    }

    private String sanitizeText(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replace("\u0000", "");
        cleaned = cleaned.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFKC);
        cleaned = cleaned.replaceAll("[\\p{Z}&&[^\\r\\n]]", " ");
        cleaned = cleaned.replaceAll(" +", " ");
        cleaned = cleaned.replaceAll("(\\r?\\n){3,}", "\n\n");
        return cleaned.trim();
    }

    private List<String> buildRetryCandidates(String text) {
        int limit = maxChars > 0 ? maxChars : 6000;
        String base = truncateText(text, limit);

        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, base);

        String stripped = stripProblematicChars(base);
        addCandidate(candidates, stripped);

        String softened = softBreakLongTokens(stripped, 120);
        addCandidate(candidates, softened);

        int half = Math.min(limit, Math.max(200, limit / 2));
        int quarter = Math.min(limit, Math.max(120, limit / 4));
        addCandidate(candidates, truncateText(softened, half));
        addCandidate(candidates, truncateText(softened, quarter));

        return new ArrayList<>(candidates);
    }

    private void addCandidate(Set<String> candidates, String text) {
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (!trimmed.isBlank()) {
            candidates.add(trimmed);
        }
    }

    private String stripProblematicChars(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String cleaned = text.replaceAll("[\\p{Cf}\\p{Cs}\\p{Co}]", " ");
        cleaned = cleaned.replaceAll("\\s+", " ");
        return cleaned.trim();
    }

    private String softBreakLongTokens(String text, int maxTokenLen) {
        if (text == null || text.isEmpty() || maxTokenLen <= 0) {
            return text;
        }
        return text.replaceAll("(\\S{" + maxTokenLen + "})(?=\\S)", "$1 ");
    }

    private String truncateText(String text, int maxChars) {
        int limit = maxChars > 0 ? maxChars : 6000;
        if (text.length() <= limit) {
            return text;
        }
        int lastSpace = text.lastIndexOf(' ', limit);
        return lastSpace > 0 ? text.substring(0, lastSpace) : text.substring(0, limit);
    }
}

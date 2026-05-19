package com.gsp26se114.chatbot_rag_be.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Strategy interface for embedding providers (Gemini, local ONNX, ...).
 */
public interface EmbeddingService {

    float[] createEmbedding(String text);

    String getModelName();

    int getDimension();

    default List<float[]> createEmbeddings(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(createEmbedding(text));
        }
        return embeddings;
    }

    /**
     * Convert float[] sang chuỗi PostgreSQL vector format.
     * Example: [0.1, 0.2, 0.3] -> "[0.1,0.2,0.3]"
     */
    default String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Parse PostgreSQL vector string to float array.
     */
    default float[] parseVector(String vectorString) {
        if (vectorString == null || vectorString.isEmpty()) {
            return new float[0];
        }
        String cleaned = vectorString.replace("[", "").replace("]", "");
        if (cleaned.isBlank()) {
            return new float[0];
        }
        String[] parts = cleaned.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }

    /**
     * Calculate cosine distance between two vectors.
     */
    default double cosineDistance(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have same dimensions");
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);
        if (normA == 0.0 || normB == 0.0) {
            return 2.0;
        }
        double cosineSimilarity = dotProduct / (normA * normB);
        cosineSimilarity = Math.max(-1.0, Math.min(1.0, cosineSimilarity));
        return 1.0 - cosineSimilarity;
    }
}

package com.gsp26se114.chatbot_rag_be.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Service to generate chat responses using Google Gemini API
 */
@Service
@Slf4j
public class GeminiChatService {

    public record AnswerWithTokens(String answer, int tokensUsed) {}

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}")
    private String chatModel;

    private final OkHttpClient httpClient;
    private final Gson gson;

    public GeminiChatService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    /**
     * Generate answer using Gemini with context from retrieved documents
     * 
     * @param context Retrieved document chunks as context
     * @param question User's question
     * @return Generated answer
     */
    public AnswerWithTokens generateAnswer(String context, String question) {
        if (apiKey == null || apiKey.isBlank()) {
            return new AnswerWithTokens("He thong chua cau hinh GEMINI_API_KEY, vui long lien he quan tri vien.", 0);
        }

        try {
            String prompt = buildPrompt(context, question);
            return callGeminiAPI(prompt);
        } catch (Exception e) {
            log.error("Failed to generate answer with Gemini", e);
            throw new RuntimeException("Failed to generate answer: " + e.getMessage(), e);
        }
    }

    /**
     * Build prompt with RAG pattern: context + instruction + question
     */
    private String buildPrompt(String context, String question) {
        // If no context (no documents), answer with general knowledge
        if (context == null || context.isBlank()) {
            return """
                    Bạn là trợ lý AI thông minh của hệ thống chatbot doanh nghiệp FPT Software.
                    Hãy trả lời câu hỏi của người dùng dựa trên kiến thức chung của bạn.

                    QUY TẮC:
                    - Luôn trả lời bằng TIẾNG VIỆT, trừ khi người dùng hỏi bằng tiếng Anh.
                    - Trả lời chính xác, hữu ích, ngắn gọn và rõ ràng.
                    - Nếu câu hỏi liên quan đến chính sách nội bộ cụ thể của công ty, hãy cho biết bạn không có thông tin chi tiết về chính sách đó và khuyên người dùng liên hệ với bộ phận HR hoặc quản lý trực tiếp.
                    - Không đề cập đến việc tải tài liệu lên hệ thống.

                    CÂU HỎI: %s

                    TRẢ LỜI:
                    """.formatted(question);
        }
        
        // With context from documents - answer based on RAG
        return """
                Bạn là trợ lý AI thông minh của hệ thống chatbot doanh nghiệp.
                Nhiệm vụ của bạn là trả lời câu hỏi DỰA TRÊN THÔNG TIN có trong tài liệu bên dưới.
                
                QUY TẮC BẮT BUỘC:
                - Luôn trả lời bằng TIẾNG VIỆT, trừ khi người dùng hỏi bằng tiếng Anh.
                - ƯU TIÊN trích xuất và sử dụng CHÍNH XÁC thông tin từ ngữ cảnh bên dưới.
                - Nếu ngữ cảnh có con số, tên riêng, hoặc dữ liệu cụ thể → Hãy TRÍCH DẪN TRỰC TIẾP.
                - CHỈ nói "không tìm thấy" KHI thông tin THỰC SỰ không có trong ngữ cảnh.
                - Trả lời ngắn gọn, rõ ràng, trực tiếp vào vấn đề.
                - Nếu có bảng biểu hoặc danh sách, hãy trình bày gọn gàng.
                
                THÔNG TIN TỪ TÀI LIỆU CÔNG TY:
                %s
                
                CÂU HỎI CỦA NGƯỜI DÙNG: %s
                
                HÃY TRẢ LỜI DỰA TRÊN THÔNG TIN TRÊN (nếu có):
                """.formatted(context, question);
    }

    /**
     * Call Gemini API to generate text
     */
    private AnswerWithTokens callGeminiAPI(String prompt) throws IOException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" 
                   + chatModel + ":generateContent?key=" + apiKey;

        // Build request body
        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        requestBody.add("contents", contents);

        // Add generation config
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.7);
        generationConfig.addProperty("topP", 0.95);
        generationConfig.addProperty("maxOutputTokens", 1024);
        requestBody.add("generationConfig", generationConfig);

        log.debug("Calling Gemini API: {}", url);

        RequestBody body = RequestBody.create(
            gson.toJson(requestBody),
            MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                log.error("Gemini API error: {} - {}", response.code(), errorBody);
                throw new IOException("Gemini API error: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            log.debug("Gemini API response: {}", responseBody);

            // Parse response
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
            
            if (candidates == null || candidates.isEmpty()) {
                log.warn("No candidates in Gemini response");
                return new AnswerWithTokens("I apologize, but I couldn't generate a response at this time.", 0);
            }

            JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
            JsonObject contentObj = firstCandidate.getAsJsonObject("content");
            JsonArray partsArray = contentObj.getAsJsonArray("parts");
            
            if (partsArray == null || partsArray.isEmpty()) {
                log.warn("No parts in Gemini response");
                return new AnswerWithTokens("I apologize, but I couldn't generate a response at this time.", 0);
            }

            String answer = partsArray.get(0).getAsJsonObject().get("text").getAsString();
            log.info("Generated answer: {} characters", answer.length());

            // Extract token usage from usageMetadata if available
            int tokensUsed = 0;
            try {
                if (jsonResponse.has("usageMetadata")) {
                    JsonObject usage = jsonResponse.getAsJsonObject("usageMetadata");
                    if (usage.has("totalTokenCount")) {
                        tokensUsed = usage.get("totalTokenCount").getAsInt();
                    }
                }
            } catch (Exception e) {
                // Fallback: estimate from text length
                tokensUsed = answer.length() / 4;
            }
            if (tokensUsed == 0) {
                tokensUsed = answer.length() / 4;
            }

            return new AnswerWithTokens(answer, tokensUsed);
        }
    }
}

package com.gsp26se114.chatbot_rag_be.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gsp26se114.chatbot_rag_be.entity.ChatMessage;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service to generate chat responses using Google Gemini API
 */
@Service
@Slf4j
public class GeminiChatService {

    public record AnswerWithTokens(String answer, int tokensUsed) {}

    /** Hướng dẫn định dạng & an toàn cho câu trả lời có tài liệu (RAG). */
    private static final String RESPONSE_GUIDELINES_RAG = """
            # HƯỚNG DẪN TRẢ LỜI
            1. Trực tiếp và chính xác: ưu tiên câu ngắn, đi thẳng vấn đề; dùng số liệu/mốc thời gian cụ thể lấy từ tài liệu khi có.
            2. Cấu trúc: dùng bullet rõ ràng; với quy trình nhiều bước hoặc nhiều nhánh điều kiện, bắt buộc dùng danh sách đa tầng (nested): mỗi ý bổ trợ/điều kiện con thụt 2 khoảng trắng so với dòng cha; không gom tất cả thành một danh sách phẳng; không dùng định dạng in đậm.
            3. Trích dẫn: nếu cần nguồn thì gom ngắn gọn ở cuối; không chèn nguồn vào từng dòng nội dung.
            4. Đầy đủ: với quy trình, kiểm tra điều kiện kèm theo (hạn, cấp phê duyệt, ngoại lệ) trong tài liệu; nếu tài liệu không nói thì nêu rõ là không có trong tài liệu.
            5. Nếu câu hỏi dạng đúng/sai: mở đầu bằng kết luận ngắn gọn "Đúng." hoặc "Không đúng." (hoặc "Đúng."/"Sai."), sau đó tối đa 1 câu giải thích trọng tâm.
            6. Tuyệt đối không hiển thị metadata kỹ thuật trong câu trả lời (ví dụ: chunk, index, vector, embedding, id nội bộ).
            7. Không lặp lại tên file trong từng bullet nội dung; nếu cần nguồn thì gom 1 dòng nguồn ngắn ở cuối.
            8. Không rút gọn URL; luôn in đầy đủ link (ví dụ https://...) để người dùng bấm/copy được nguyên vẹn.
            9. Nếu câu hỏi về kênh phản ánh ẩn danh và context có thông tin tương ứng, phải liệt kê đầy đủ 2 hình thức:
               - Link trực tuyến: ethicsreporting.fpt-software.com
               - Gửi thư ẩn danh: Bộ phận tuân thủ (LRC) - FPT Software, Tòa nhà FPT Cầu Giấy, Phố Duy Tân, Hà Nội.
            10. Khi trả lời dựa trên tài liệu, luôn trích xuất đầy đủ các điều kiện pháp lý đi kèm: bộ phận phê duyệt cụ thể (vd: LRC, ISM), yêu cầu phê duyệt bằng văn bản, và giới hạn thời gian/gia hạn (vd: gia hạn hằng năm). Không được bỏ sót các điều kiện này nếu context có nêu.
            11. Khi câu hỏi có nhiều vế, bắt buộc tách và trả lời đầy đủ từng vế; không được trả lời nửa chừng hay bỏ sót vế nào.
            12. Tránh lặp ý: không nhắc lại cùng một nội dung ở nhiều bullet cùng cấp; nếu cần chi tiết hóa thì đưa chi tiết vào bullet con (thụt dòng) thay vì lặp lại câu cha.
            13. Trích dẫn nguồn: tối đa một dòng nguồn ngắn ở cuối câu trả lời (nếu cần); không chèn nhiều nguồn rải rác.

            # QUYỀN TRUY CẬP
            Mỗi đoạn trong "THÔNG TIN TỪ TÀI LIỆU" có dòng đầu `[ACCESS: GRANTED | ...]` — đó là nội dung hệ thống đã xác nhận người dùng được phép dùng.
            Chỉ trả lời dựa trên các đoạn đó; không suy diễn hay bịa nội dung của tài liệu/mục không xuất hiện trong context.
            """;

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
    public AnswerWithTokens generateAnswer(String context, String question, List<ChatMessage> history) {
        if (apiKey == null || apiKey.isBlank()) {
            return new AnswerWithTokens("He thong chua cau hinh GEMINI_API_KEY, vui long lien he quan tri vien.", 0);
        }

        try {
            String prompt = buildPrompt(context, question, history);
            return callGeminiAPI(prompt, 2048);
        } catch (Exception e) {
            log.error("Failed to generate answer with Gemini", e);
            throw new RuntimeException("Failed to generate answer: " + e.getMessage(), e);
        }
    }

    /**
     * Build prompt with RAG pattern: context + instruction + question
     */
    private String buildPrompt(String context, String question, List<ChatMessage> history) {
        StringBuilder historyBlock = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            historyBlock.append("LỊCH SỬ HỘI THOẠI GẦN ĐÂY:\n");
            for (ChatMessage msg : history) {
                if ("USER".equals(msg.getRole())) {
                    historyBlock.append("Người dùng: ").append(msg.getContent()).append("\n");
                } else {
                    historyBlock.append("Trợ lý: ").append(msg.getContent()).append("\n");
                }
            }
            historyBlock.append("\n");
        }
        String historyText = historyBlock.toString();

        // If no context, the controller should block and return "no_match".
        // Keep a defensive fallback to avoid any external-knowledge answer.
        if (context == null || context.isBlank()) {
            return """
                    Bạn là trợ lý AI nội bộ.
                    Không có thông tin tài liệu trong context cho câu hỏi hiện tại.
                    Hãy trả lời đúng 1 câu sau và không thêm gì:
                    Xin lỗi, tôi không tìm thấy thông tin liên quan đến câu hỏi của bạn trong tài liệu nội bộ.
                    """;
        }
        
        // With context from documents - answer based on RAG
        return """
                Bạn là chuyên gia về quy định nội bộ.
                Hãy trả lời câu hỏi của người dùng chỉ dựa trên thông tin từ tài liệu công ty bên dưới.
                Tuyệt đối không dùng kiến thức ngoài tài liệu/context được cung cấp.

                QUY TẮC NGÔN NGỮ:
                - Luôn trả lời bằng TIẾNG VIỆT, trừ khi người dùng hỏi bằng tiếng Anh.

                %s

                QUY TẮC BỔ SUNG:
                - Ưu tiên số liệu, mốc, điều kiện lấy trực tiếp từ đoạn tài liệu.
                - Với câu hỏi yes/no (đúng hay không, đúng hay sai), phải trả lời kết luận trước: Đúng/Không đúng (hoặc Đúng/Sai), ngắn gọn, không dài dòng.
                - Câu hỏi có nhiều vế thì phải trả lời đủ từng vế theo từng bullet.
                - Quy trình/điều kiện nhiều tầng: dùng bullet lồng nhau, mỗi cấp con thụt đúng 2 khoảng trắng; điều kiện phụ và ngoại lệ đặt dưới bước cha, không lặp lại toàn bộ câu cha.
                - Luôn tìm và liệt kê rõ 3 nhóm điều kiện nếu có trong context: (1) bộ phận phê duyệt (LRC/ISM), (2) hình thức phê duyệt bằng văn bản, (3) thời hạn/gia hạn hằng năm.
                - Nếu tài liệu không chứa phần liên quan: trả lời đúng 1 câu sau và không thêm gì:
                  Xin lỗi, tôi không tìm thấy thông tin liên quan đến câu hỏi của bạn trong tài liệu nội bộ.
                - Không đề cập đến việc tải tài liệu lên hệ thống.
                - Không dùng in đậm trong câu trả lời và không nhắc tên file trong phần nội dung chính.

                THÔNG TIN TỪ TÀI LIỆU CÔNG TY:
                %s

                %sCÂU HỎI HIỆN TẠI: %s

                TRẢ LỜI:
                """.formatted(RESPONSE_GUIDELINES_RAG, context, historyText, question);
    }

    /**
     * Call Gemini API to generate text
     */
    private AnswerWithTokens callGeminiAPI(String prompt, int maxOutputTokens) throws IOException {
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
        generationConfig.addProperty("maxOutputTokens", maxOutputTokens);
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
            String finishReason = firstCandidate.has("finishReason")
                    ? firstCandidate.get("finishReason").getAsString()
                    : "";
            JsonObject contentObj = firstCandidate.getAsJsonObject("content");
            JsonArray partsArray = contentObj.getAsJsonArray("parts");
            
            if (partsArray == null || partsArray.isEmpty()) {
                log.warn("No parts in Gemini response");
                return new AnswerWithTokens("I apologize, but I couldn't generate a response at this time.", 0);
            }

            String answer = partsArray.get(0).getAsJsonObject().get("text").getAsString();
            log.info("Generated answer: {} characters", answer.length());

            // Guardrail: if response is cut by token limit, regenerate once with a stronger instruction.
            if ("MAX_TOKENS".equalsIgnoreCase(finishReason)) {
                log.warn("Gemini response hit MAX_TOKENS. Retrying once with larger output budget.");
                String fullAnswerPrompt = prompt + """

                        
                        Lưu ý quan trọng:
                        - Câu trả lời trước đã bị ngắt do giới hạn độ dài.
                        - Hãy trả lời lại từ đầu, đầy đủ tất cả các ý, không bỏ sót, không cắt giữa chừng.
                        """;
                return callGeminiAPI(fullAnswerPrompt, 3072);
            }

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

package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.config.TenantContext;
import com.gsp26se114.chatbot_rag_be.service.TenantTierResolver;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gsp26se114.chatbot_rag_be.entity.ChatMessage;
import com.gsp26se114.chatbot_rag_be.entity.ChatbotMode;

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
            3. Trích dẫn: không chèn dòng nguồn trong câu trả lời; hệ thống sẽ hiển thị nguồn ở khối riêng bên ngoài.
            4. Đầy đủ: với quy trình, kiểm tra điều kiện kèm theo (hạn, cấp phê duyệt, ngoại lệ) trong tài liệu; nếu tài liệu không nói thì nêu rõ là không có trong tài liệu.
            5. Nếu câu hỏi dạng đúng/sai: mở đầu bằng kết luận ngắn gọn "Đúng." hoặc "Không đúng." (hoặc "Đúng."/"Sai."), sau đó tối đa 1 câu giải thích trọng tâm.
            6. Tuyệt đối không hiển thị metadata kỹ thuật trong câu trả lời (ví dụ: chunk, index, vector, embedding, id nội bộ).
            7. Không lặp lại tên file trong từng bullet nội dung.
            8. Không rút gọn URL; luôn in đầy đủ link (ví dụ https://...) để người dùng bấm/copy được nguyên vẹn.
            9. Nếu câu hỏi về kênh phản ánh ẩn danh và context có thông tin tương ứng, phải liệt kê đầy đủ 2 hình thức:
               - Link trực tuyến: ethicsreporting.fpt-software.com
               - Gửi thư ẩn danh: Bộ phận tuân thủ (LRC) - FPT Software, Tòa nhà FPT Cầu Giấy, Phố Duy Tân, Hà Nội.
            10. Khi trả lời dựa trên tài liệu, luôn trích xuất đầy đủ các điều kiện pháp lý đi kèm: bộ phận phê duyệt cụ thể (vd: LRC, ISM), yêu cầu phê duyệt bằng văn bản, và giới hạn thời gian/gia hạn (vd: gia hạn hằng năm). Không được bỏ sót các điều kiện này nếu context có nêu.
            11. Khi câu hỏi có nhiều vế, bắt buộc tách và trả lời đầy đủ từng vế; không được trả lời nửa chừng hay bỏ sót vế nào.
            12. Tránh lặp ý: không nhắc lại cùng một nội dung ở nhiều bullet cùng cấp; nếu cần chi tiết hóa thì đưa chi tiết vào bullet con (thụt dòng) thay vì lặp lại câu cha.
            13. Không in bất kỳ dòng tổng kết nguồn nào trong câu trả lời, kể cả dạng "Nguồn: ...".

            # QUYỀN TRUY CẬP
            Mỗi đoạn trong "THÔNG TIN TỪ TÀI LIỆU" có dòng đầu `[ACCESS: GRANTED | ...]` — đó là nội dung hệ thống đã xác nhận người dùng được phép dùng.
            Chỉ trả lời dựa trên các đoạn đó; không suy diễn hay bịa nội dung của tài liệu/mục không xuất hiện trong context.
            """;

    @Value("${chiasegpu.api-key}")
    private String apiKey;

    @Value("${chiasegpu.base-url:https://llm.chiasegpu.vn/v1}")
    private String baseUrl;

    @Value("${gemini.models.starter:gemini-2.5-flash}")
    private String starterModel;

    @Value("${gemini.models.standard:gemini-2.5-flash}")
    private String standardModel;

    @Value("${gemini.models.enterprise:gemini-2.5-pro}")
    private String enterpriseModel;

    @Value("${gemini.models.starter:ts/gemini-2.5-flash}")
    private String defaultModel;

    @Autowired
    private TenantTierResolver tenantTierResolver;

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
    public AnswerWithTokens generateAnswer(
            String context, String question, List<ChatMessage> history, ChatbotMode mode) {
        if (apiKey == null || apiKey.isBlank()) {
            return new AnswerWithTokens("He thong chua cau hinh CHIASEGPU_API_KEY, vui long lien he quan tri vien.", 0);
        }

        try {
            String prompt = buildPrompt(context, question, history, mode);
            return callGeminiAPI(prompt, 2048);
        } catch (Exception e) {
            log.error("Failed to generate answer with Gemini", e);
            throw new RuntimeException("Failed to generate answer: " + e.getMessage(), e);
        }
    }

    /**
     * Build prompt with RAG pattern: context + instruction + question
     */
    String buildPrompt(String context, String question, List<ChatMessage> history, ChatbotMode mode) {
        ChatbotMode resolvedMode = mode != null ? mode : ChatbotMode.BALANCED;
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

        if (context == null || context.isBlank()) {
            return buildNoContextPrompt(resolvedMode, question, historyText);
        }
        
        // With context from documents - answer based on RAG
        String strictPrompt = """
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

        if (resolvedMode == ChatbotMode.STRICT) {
            return mandatoryCitationGuideline() + "\n\n" + strictPrompt;
        }
        return buildConversationalRagPrompt(resolvedMode, context, question, historyText);
    }

    private String buildNoContextPrompt(ChatbotMode mode, String question, String historyText) {
        if (mode == ChatbotMode.STRICT) {
            return """
                    Bạn là trợ lý AI nội bộ ở chế độ STRICT.
                    Không có thông tin tài liệu trong context cho câu hỏi hiện tại.
                    Hãy trả lời đúng 1 câu sau và không thêm gì:
                    Xin lỗi, tôi không tìm thấy thông tin liên quan đến câu hỏi của bạn trong tài liệu nội bộ.
                    """;
        }

        String modeGuideline = mode == ChatbotMode.FLEXIBLE
                ? """
                  - Trò chuyện tự nhiên và hỗ trợ đầy đủ bằng kiến thức phổ thông khi phù hợp.
                  - Nếu câu hỏi đề cập thông tin riêng của công ty mà không có tài liệu nội bộ, phải nói rõ rằng bạn không tìm thấy thông tin đó trong tài liệu nội bộ; không được bịa dữ kiện của công ty.
                  """
                : """
                  - Chào hỏi, trò chuyện xã giao và trả lời kiến thức phổ thông nhẹ nhàng một cách tự nhiên.
                  - Nếu câu hỏi liên quan đến công ty, chính sách hoặc quy trình nội bộ mà không có tài liệu phù hợp, hãy trả lời đúng 1 câu: Xin lỗi, tôi không tìm thấy thông tin liên quan đến câu hỏi của bạn trong tài liệu nội bộ.
                  - Không được bịa dữ kiện riêng của công ty.
                  """;

        return """
                Bạn là trợ lý AI hữu ích ở chế độ %s.

                QUY TẮC:
                - Luôn trả lời bằng TIẾNG VIỆT, trừ khi người dùng hỏi bằng tiếng Anh.
                %s

                %sCÂU HỎI HIỆN TẠI: %s

                TRẢ LỜI:
                """.formatted(mode.name(), modeGuideline, historyText, question);
    }

    private String buildConversationalRagPrompt(
            ChatbotMode mode, String context, String question, String historyText) {
        String modeGuideline = mode == ChatbotMode.FLEXIBLE
                ? """
                  - Trò chuyện tự nhiên và có thể bổ sung kiến thức phổ thông để giúp người dùng.
                  - Khi dùng kiến thức ngoài tài liệu, phải phân biệt rõ với thông tin nội bộ lấy từ tài liệu.
                  """
                : """
                  - Có thể chào hỏi, trò chuyện xã giao và trả lời kiến thức phổ thông nhẹ nhàng.
                  - Với câu hỏi về công ty, chính sách hoặc quy trình nội bộ, chỉ nêu dữ kiện có trong tài liệu được cung cấp.
                  """;

        return """
                Bạn là trợ lý AI hữu ích ở chế độ %s.

                QUY TẮC CHẾ ĐỘ:
                - Luôn trả lời bằng TIẾNG VIỆT, trừ khi người dùng hỏi bằng tiếng Anh.
                %s
                - Không được bịa dữ kiện riêng của công ty.

                %s

                %s

                THÔNG TIN TỪ TÀI LIỆU CÔNG TY:
                %s

                %sCÂU HỎI HIỆN TẠI: %s

                TRẢ LỜI:
                """.formatted(
                mode.name(),
                modeGuideline,
                mandatoryCitationGuideline(),
                RESPONSE_GUIDELINES_RAG,
                context,
                historyText,
                question);
    }

    private String mandatoryCitationGuideline() {
        return """
                YÊU CẦU TRÍCH DẪN BẮT BUỘC:
                - Khi sử dụng thông tin từ context, câu trả lời phải kết thúc bằng đúng một dòng ngắn theo mẫu: Nguồn: <tên tài liệu>.
                - Lấy tên tài liệu từ nhãn [ACCESS: GRANTED | Tài liệu: ...] trong context.
                """;
    }

    /**
     * Call Gemini API to generate text
     */
    private AnswerWithTokens callGeminiAPI(String prompt, int maxOutputTokens) throws IOException {
        String modelToUse = resolveChatModel();
        log.info("Using LLM model: {} for tenant tier", modelToUse);
        String url = baseUrl + "/chat/completions";

        // Build OpenAI-compatible request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelToUse);

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);
        requestBody.add("messages", messages);

        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", maxOutputTokens);
        requestBody.addProperty("stream", false);

        log.debug("Calling ChiaseGPU API: {}", url);

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
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                log.error("ChiaseGPU API error: {} - {}", response.code(), errorBody);
                throw new IOException("ChiaseGPU API error: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            log.debug("ChiaseGPU API response: {}", responseBody);

            // ChiaseGPU may return SSE streaming (data: {...}) even for non-stream
            // requests. Handle both SSE and single-object responses.
            String answer = "";
            String finishReason = "";
            int tokensUsed = 0;
            boolean isSse = responseBody.trim().startsWith("data:");

            if (isSse) {
                StringBuilder contentBuilder = new StringBuilder();
                for (String rawLine : responseBody.split("\n")) {
                    String line = rawLine.trim();
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if (payload.isEmpty() || "[DONE]".equals(payload)) {
                        continue;
                    }
                    try {
                        JsonObject chunk = gson.fromJson(payload, JsonObject.class);
                        JsonArray chunkChoices = chunk.getAsJsonArray("choices");
                        if (chunkChoices != null && !chunkChoices.isEmpty()) {
                            JsonObject firstChoice = chunkChoices.get(0).getAsJsonObject();
                            if (firstChoice.has("delta") && firstChoice.get("delta").isJsonObject()) {
                                JsonObject delta = firstChoice.getAsJsonObject("delta");
                                if (delta.has("content") && !delta.get("content").isJsonNull()) {
                                    contentBuilder.append(delta.get("content").getAsString());
                                }
                            }
                            if (firstChoice.has("message") && firstChoice.get("message").isJsonObject()) {
                                JsonObject message = firstChoice.getAsJsonObject("message");
                                if (message.has("content") && !message.get("content").isJsonNull()) {
                                    contentBuilder.append(message.get("content").getAsString());
                                }
                            }
                            if (firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isJsonNull()) {
                                finishReason = firstChoice.get("finish_reason").getAsString();
                            }
                        }
                        if (chunk.has("usage") && chunk.get("usage").isJsonObject()) {
                            JsonObject usage = chunk.getAsJsonObject("usage");
                            if (usage.has("total_tokens") && !usage.get("total_tokens").isJsonNull()) {
                                tokensUsed = usage.get("total_tokens").getAsInt();
                            }
                        }
                    } catch (Exception ex) {
                        log.debug("Skipping unparseable SSE chunk: {}", payload);
                    }
                }
                answer = contentBuilder.toString();
            } else {
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                JsonArray choices = jsonResponse.getAsJsonArray("choices");
                if (choices == null || choices.isEmpty()) {
                    log.warn("No choices in ChiaseGPU response");
                    return new AnswerWithTokens("I apologize, but I couldn't generate a response at this time.", 0);
                }
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                finishReason = firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isJsonNull()
                        ? firstChoice.get("finish_reason").getAsString() : "";
                JsonObject messageObj = firstChoice.getAsJsonObject("message");
                if (messageObj == null || !messageObj.has("content") || messageObj.get("content").isJsonNull()) {
                    log.warn("No message content in ChiaseGPU response");
                    return new AnswerWithTokens("I apologize, but I couldn't generate a response at this time.", 0);
                }
                answer = messageObj.get("content").getAsString();
                if (jsonResponse.has("usage") && jsonResponse.get("usage").isJsonObject()) {
                    JsonObject usage = jsonResponse.getAsJsonObject("usage");
                    if (usage.has("total_tokens") && !usage.get("total_tokens").isJsonNull()) {
                        tokensUsed = usage.get("total_tokens").getAsInt();
                    }
                }
            }

            if (answer == null || answer.isEmpty()) {
                log.warn("Empty answer from ChiaseGPU (finishReason={}, sse={})", finishReason, isSse);
                return new AnswerWithTokens("I apologize, but I couldn't generate a response at this time.", 0);
            }

            log.info("Generated answer: {} characters", answer.length());

            // Guardrail: if response cut by length, regenerate once with larger budget
            if ("length".equalsIgnoreCase(finishReason)) {
                log.warn("ChiaseGPU response hit length limit. Retrying once with larger output budget.");
                String fullAnswerPrompt = prompt + """

                        
                        Lưu ý quan trọng:
                        - Câu trả lời trước đã bị ngắt do giới hạn độ dài.
                        - Hãy trả lời lại từ đầu, đầy đủ tất cả các ý, không bỏ sót, không cắt giữa chừng.
                        """;
                return callGeminiAPI(fullAnswerPrompt, 3072);
            }

            if (tokensUsed == 0) {
                tokensUsed = answer.length() / 4;
            }

            return new AnswerWithTokens(answer, tokensUsed);
        }
    }

    /**
     * Resolves which Gemini model to use based on current tenant tier.
     * - ENTERPRISE → enterpriseModel (Gemini Pro)
     * - STANDARD → standardModel (Gemini Flash) 
     * - STARTER/TRIAL → starterModel (Gemini Flash)
     * - No tenant context → defaultModel
     */
    private String resolveChatModel() {
        java.util.UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return defaultModel;
        }
        String tier = TenantContext.getCurrentTier();
        if (tier == null || tier.isBlank()) {
            tier = tenantTierResolver.resolveTier(tenantId);
        }
        switch (tier) {
            case "ENTERPRISE":
                return enterpriseModel;
            case "STANDARD":
                return standardModel;
            case "STARTER":
            case "TRIAL":
            default:
                return starterModel;
        }
    }
}

package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.ChatbotMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiChatServicePromptTest {

    private final GeminiChatService service = new GeminiChatService();

    @Test
    void defaultsMissingAndInvalidModesToBalanced() {
        assertThat(ChatbotMode.from(null)).isEqualTo(ChatbotMode.BALANCED);
        assertThat(ChatbotMode.from("")).isEqualTo(ChatbotMode.BALANCED);
        assertThat(ChatbotMode.from("unknown")).isEqualTo(ChatbotMode.BALANCED);
        assertThat(ChatbotMode.from(" flexible ")).isEqualTo(ChatbotMode.FLEXIBLE);
    }

    @Test
    void strictModeDeclinesGreetingWhenNoDocumentMatches() {
        String prompt = service.buildPrompt("", "hi", List.of(), ChatbotMode.STRICT);

        assertThat(ChatbotMode.STRICT.allowsAnswerWithoutContext()).isFalse();
        assertThat(prompt)
                .contains("chế độ STRICT")
                .contains("Xin lỗi, tôi không tìm thấy thông tin liên quan đến câu hỏi của bạn trong tài liệu nội bộ.")
                .doesNotContain("kiến thức phổ thông nhẹ nhàng");
    }

    @Test
    void balancedModeAllowsGreetingAndLightGeneralKnowledgeWithoutContext() {
        String prompt = service.buildPrompt("", "hi", List.of(), ChatbotMode.BALANCED);

        assertThat(ChatbotMode.BALANCED.allowsAnswerWithoutContext()).isTrue();
        assertThat(prompt)
                .contains("chế độ BALANCED")
                .contains("Chào hỏi, trò chuyện xã giao")
                .contains("kiến thức phổ thông nhẹ nhàng")
                .contains("Không được bịa dữ kiện riêng của công ty");
    }

    @Test
    void flexibleModeAllowsFullyConversationalGeneralKnowledgeWithoutContext() {
        String prompt = service.buildPrompt(
                "", "thủ đô nước Pháp là gì?", List.of(), ChatbotMode.FLEXIBLE);

        assertThat(ChatbotMode.FLEXIBLE.allowsAnswerWithoutContext()).isTrue();
        assertThat(prompt)
                .contains("chế độ FLEXIBLE")
                .contains("Trò chuyện tự nhiên và hỗ trợ đầy đủ bằng kiến thức phổ thông")
                .contains("thủ đô nước Pháp là gì?");
    }

    @Test
    void balancedModeCanAnswerLightGeneralKnowledgeWithoutContext() {
        String prompt = service.buildPrompt(
                "", "thủ đô nước Pháp là gì?", List.of(), ChatbotMode.BALANCED);

        assertThat(prompt)
                .contains("kiến thức phổ thông nhẹ nhàng")
                .contains("thủ đô nước Pháp là gì?");
    }

    @ParameterizedTest
    @EnumSource(ChatbotMode.class)
    void leavePolicyContextRequiresCitationInEveryMode(ChatbotMode mode) {
        String context = """
                [ACCESS: GRANTED | Tài liệu: leave-policy.pdf]
                Nhân viên có 12 ngày nghỉ phép mỗi năm.
                """;

        String prompt = service.buildPrompt(
                context, "Chính sách nghỉ phép như thế nào?", List.of(), mode);

        assertThat(prompt)
                .contains("Nhân viên có 12 ngày nghỉ phép mỗi năm.")
                .contains("YÊU CẦU TRÍCH DẪN BẮT BUỘC")
                .contains("Nguồn: <tên tài liệu>");
    }
}

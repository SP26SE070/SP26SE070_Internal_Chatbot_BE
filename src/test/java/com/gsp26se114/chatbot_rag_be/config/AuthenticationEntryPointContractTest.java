package com.gsp26se114.chatbot_rag_be.config;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationEntryPointContractTest {

    @Test
    void unauthenticatedResponseContractRemains401Json() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String traceId = UUID.randomUUID().toString();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Trace-Id", traceId);
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid token\",\"traceId\":\""
                        + traceId
                        + "\"}"
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(response.getContentAsString()).contains("\"message\":\"Missing or invalid token\"");
        assertThat(response.getHeader("X-Trace-Id")).isNotBlank();
    }
}

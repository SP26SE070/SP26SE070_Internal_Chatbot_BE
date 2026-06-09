package com.gsp26se114.chatbot_rag_be.exception;

import com.gsp26se114.chatbot_rag_be.payload.response.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void badRequestExceptionReturns400() {
        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(new BadRequestException("Mã OTP không đúng!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("Mã OTP không đúng!");
    }
}

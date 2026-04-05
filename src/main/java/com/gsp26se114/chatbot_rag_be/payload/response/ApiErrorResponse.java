package com.gsp26se114.chatbot_rag_be.payload.response;

import java.util.Map;
import java.util.UUID;

public record ApiErrorResponse(
        String code,
        String message,
        String traceId,
        Map<String, String> errors
) {
    public ApiErrorResponse(String code, String message, Map<String, String> errors) {
        this(code, message, UUID.randomUUID().toString(), errors);
    }

    public ApiErrorResponse(String code, String message) {
        this(code, message, UUID.randomUUID().toString(), null);
    }
}

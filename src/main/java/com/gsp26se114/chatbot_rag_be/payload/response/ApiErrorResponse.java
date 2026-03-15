package com.gsp26se114.chatbot_rag_be.payload.response;

import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        Map<String, String> errors
) {}

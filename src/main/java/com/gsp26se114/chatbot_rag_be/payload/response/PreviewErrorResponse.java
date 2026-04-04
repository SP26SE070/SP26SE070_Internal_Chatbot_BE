package com.gsp26se114.chatbot_rag_be.payload.response;

public record PreviewErrorResponse(
        String code,
        String message,
        String traceId
) {}

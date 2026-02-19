package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotBlank;

public record RejectRequest(
    @NotBlank(message = "Lý do từ chối không được để trống")
    String reason
) {}

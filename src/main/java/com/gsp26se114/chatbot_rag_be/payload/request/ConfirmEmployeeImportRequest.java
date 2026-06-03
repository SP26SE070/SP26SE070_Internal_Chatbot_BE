package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfirmEmployeeImportRequest(
        @NotNull(message = "importSessionId không được để trống")
        UUID importSessionId
) {}

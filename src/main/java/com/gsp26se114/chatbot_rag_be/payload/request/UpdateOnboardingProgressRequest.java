package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateOnboardingProgressRequest(
        @NotNull(message = "readPercent không được để trống")
        @Min(value = 0, message = "readPercent phải >= 0")
        @Max(value = 100, message = "readPercent phải <= 100")
        Integer readPercent
) {}

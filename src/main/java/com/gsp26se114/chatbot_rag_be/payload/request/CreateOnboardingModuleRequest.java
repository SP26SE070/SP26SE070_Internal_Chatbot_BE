package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOnboardingModuleRequest(
        @NotBlank(message = "title không được để trống")
        @Size(max = 200, message = "title không được quá 200 ký tự")
        String title,

        @Size(max = 1000, message = "summary không được quá 1000 ký tự")
        String summary,

        @NotBlank(message = "content không được để trống")
        String content,

        @Min(value = 0, message = "displayOrder phải >= 0")
        Integer displayOrder,

        @Min(value = 1, message = "estimatedMinutes phải >= 1")
        @Max(value = 180, message = "estimatedMinutes phải <= 180")
        Integer estimatedMinutes,

        List<String> requiredPermissions
) {}

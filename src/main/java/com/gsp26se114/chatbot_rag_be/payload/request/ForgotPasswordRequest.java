package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    String email
) {}
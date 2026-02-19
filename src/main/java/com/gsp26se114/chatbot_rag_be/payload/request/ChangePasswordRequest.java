package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        String oldPassword,  // Optional nếu mustChangePassword = true
        
        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^])[A-Za-z\\d@$!%*?&#^]{8,}$",
            message = "Mật khẩu phải có ít nhất 8 ký tự, bao gồm chữ hoa, chữ thường, số và ký tự đặc biệt"
        )
        String newPassword
) {}

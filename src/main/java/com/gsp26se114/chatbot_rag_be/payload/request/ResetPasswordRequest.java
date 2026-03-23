package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Đặt lại mật khẩu sau khi đã verify OTP (resetSessionToken từ bước verify-reset-otp).
 * Rule mật khẩu đồng bộ với {@link ChangePasswordRequest}.
 */
public record ResetPasswordRequest(
    @NotBlank(message = "resetSessionToken không được để trống")
    @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "resetSessionToken không đúng định dạng"
    )
    String resetSessionToken,

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^])[A-Za-z\\d@$!%*?&#^]{8,}$",
            message = "Mật khẩu phải có ít nhất 8 ký tự, bao gồm chữ hoa, chữ thường, số và ký tự đặc biệt"
    )
    String newPassword
) {}
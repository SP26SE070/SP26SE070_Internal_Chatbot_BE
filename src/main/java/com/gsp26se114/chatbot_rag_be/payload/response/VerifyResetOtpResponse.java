package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trả về sau khi xác thực OTP đúng; FE dùng {@code resetSessionToken} khi gọi reset-password.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResetOtpResponse {
    private String message;
    private String resetSessionToken;
}

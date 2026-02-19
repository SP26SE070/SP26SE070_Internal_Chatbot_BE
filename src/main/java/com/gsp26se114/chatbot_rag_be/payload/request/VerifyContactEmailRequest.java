package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyContactEmailRequest {
    @NotBlank
    @Email
    private String newContactEmail;
    
    @NotBlank(message = "OTP không được để trống")
    @Size(min = 6, max = 6, message = "OTP phải có 6 chữ số")
    private String otp;
}

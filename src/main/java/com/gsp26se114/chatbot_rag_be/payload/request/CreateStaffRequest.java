package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateStaffRequest {
    /** Email thật để nhận thông báo (welcome, OTP, ...). Đăng nhập dùng email ảo staff@system.com, staff2@system.com, ... */
    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email must be valid")
    private String contactEmail;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String phone;
}

package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(
        @NotBlank(message = "Mã phòng ban không được để trống")
        @Size(max = 50, message = "Mã phòng ban không được quá 50 ký tự")
        String code,  // HR, DEV, SALES, FINANCE
        
        @NotBlank(message = "Tên phòng ban không được để trống")
        @Size(max = 255, message = "Tên phòng ban không được quá 255 ký tự")
        String name,  // Phòng Nhân Sự, Phòng Phát Triển
        
        @Size(max = 500, message = "Mô tả không được quá 500 ký tự")
        String description
) {}

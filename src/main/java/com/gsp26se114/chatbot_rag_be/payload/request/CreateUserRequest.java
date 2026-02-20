package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateUserRequest(
        @NotBlank(message = "Họ tên không được để trống")
        String fullName,  // Họ tên đầy đủ để generate login email
        
        @NotBlank(message = "Contact email không được để trống")
        @Email(message = "Contact email không hợp lệ")
        String contactEmail,  // Email thật để nhận thông báo
        
        @Size(max = 20, message = "Số điện thoại không được quá 20 ký tự")
        String phoneNumber,
        
        @Past(message = "Ngày sinh phải là ngày trong quá khứ")
        LocalDate dateOfBirth,
        
        @Size(max = 500, message = "Địa chỉ không được quá 500 ký tự")
        String address,
        
        @NotNull(message = "Role không được để trống")
        Integer roleId,  // Foreign key to roles table
        
        Integer departmentId,  // Foreign key to departments table (optional)
        
        List<String> permissions  // Permissions bổ sung (optional)
) {}

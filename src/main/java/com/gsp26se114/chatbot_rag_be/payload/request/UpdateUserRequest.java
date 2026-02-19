package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateUserRequest(
        @Size(max = 255, message = "Họ tên không được quá 255 ký tự")
        String fullName,
        
        @Size(max = 20, message = "Số điện thoại không được quá 20 ký tự")
        String phoneNumber,
        
        @Pattern(regexp = "^[A-Z0-9-]*$", message = "Mã nhân viên chỉ được chứa chữ in hoa, số và dấu gạch ngang")
        @Size(max = 20, message = "Mã nhân viên không được quá 20 ký tự")
        String employeeCode,
        
        @Past(message = "Ngày sinh phải là ngày trong quá khứ")
        LocalDate dateOfBirth,
        
        @Size(max = 500, message = "Địa chỉ không được quá 500 ký tự")
        String address,
        
        Integer departmentId,
        Integer roleId
) {}

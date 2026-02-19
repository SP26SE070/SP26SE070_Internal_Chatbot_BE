package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for user profile information
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {
    private UUID id;
    private String email;              // Email đăng nhập
    private String contactEmail;       // Email thật để nhận thông báo
    private String fullName;           // Họ tên đầy đủ
    private String phoneNumber;        // Số điện thoại
    private String employeeCode;       // Mã nhân viên
    private LocalDate dateOfBirth;     // Ngày sinh
    private String address;            // Địa chỉ
    private String roleName;           // Role name (Employee, Manager, Admin, etc)
    private String departmentName;     // Department name (Development, HR, Sales, etc)
    private UUID tenantId;             // UUID tenant
    private LocalDateTime createdAt;   // Ngày tạo
    private LocalDateTime updatedAt;   // Lần cập nhật cuối
    private LocalDateTime lastLoginAt; // Lần đăng nhập cuối
}

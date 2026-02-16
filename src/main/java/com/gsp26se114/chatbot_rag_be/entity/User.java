package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email; // Email đăng nhập - UNIQUE GLOBALLY (multi-tenant không được trùng)

    @Column(unique = true)
    private String contactEmail; // Email thật để nhận thông báo (OPTIONAL, đã verify khi lưu vào DB)

    @Column(nullable = false)
    private String password;
    
    private String fullName; // Họ tên đầy đủ
    
    private String phoneNumber; // Số điện thoại
    
    @Column(unique = true)
    private String employeeCode; // Mã nhân viên (unique trong toàn hệ thống)
    
    private LocalDate dateOfBirth; // Ngày sinh
    
    @Column(length = 500)
    private String address; // Địa chỉ

    @Column(name = "role_id", nullable = false)
    private Integer roleId; // Foreign key to roles table

    @Column(name = "department_id")
    private Integer departmentId; // Foreign key to departments table

    @Column(name = "tenant_id")
    private UUID tenantId;

    private String resetPasswordToken;
    private LocalDateTime tokenExpiry;
    
    @Column(nullable = false)
    private Boolean mustChangePassword = false; // Bắt buộc đổi mật khẩu lần đầu login

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt;
    
    private LocalDateTime lastLoginAt;
}
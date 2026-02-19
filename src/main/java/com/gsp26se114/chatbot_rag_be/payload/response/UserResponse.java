package com.gsp26se114.chatbot_rag_be.payload.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String contactEmail,
        String fullName,
        String phoneNumber,
        String employeeCode,
        LocalDate dateOfBirth,
        String address,
        Integer roleId,
        String roleCode,
        String roleName,
        Integer departmentId,
        String departmentCode,
        String departmentName,
        UUID tenantId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt
) {}

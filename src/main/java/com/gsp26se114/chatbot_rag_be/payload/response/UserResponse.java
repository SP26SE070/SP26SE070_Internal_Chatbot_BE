package com.gsp26se114.chatbot_rag_be.payload.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String contactEmail,
        String fullName,
        String phoneNumber,
        LocalDate dateOfBirth,
        String address,
        String roleName,
        String departmentName,
        String tenantName,
        List<String> permissions,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt
) {}

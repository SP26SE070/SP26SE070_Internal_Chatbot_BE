package com.gsp26se114.chatbot_rag_be.payload.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tenant user listing. Single primary role per user ({@code roleId} = {@code roles.role_id}).
 */
public record UserResponse(
        UUID id,
        String email,
        String contactEmail,
        String fullName,
        String phoneNumber,
        LocalDate dateOfBirth,
        String address,
        Integer roleId,
        String roleCode,
        String roleName,
        String departmentName,
        String tenantName,
        List<String> permissions,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt,
        Boolean emailSent
) {}

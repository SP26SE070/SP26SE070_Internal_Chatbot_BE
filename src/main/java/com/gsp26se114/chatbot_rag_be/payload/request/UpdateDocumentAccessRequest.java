package com.gsp26se114.chatbot_rag_be.payload.request;

import com.gsp26se114.chatbot_rag_be.entity.DocumentVisibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateDocumentAccessRequest(
        @NotNull(message = "Visibility không được để trống")
        DocumentVisibility visibility,

        @NotNull(message = "minimumRoleLevel không được để trống")
        @Min(value = 1, message = "minimumRoleLevel phải từ 1 đến 5")
        @Max(value = 5, message = "minimumRoleLevel phải từ 1 đến 5")
        Integer minimumRoleLevel,
        
        List<Integer> accessibleDepartments,  // Required if visibility = SPECIFIC_DEPARTMENTS
        
        List<Integer> accessibleRoles  // Required if visibility = SPECIFIC_ROLES
) {}

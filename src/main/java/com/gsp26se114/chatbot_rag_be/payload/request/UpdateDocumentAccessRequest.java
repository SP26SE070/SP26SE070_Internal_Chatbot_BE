package com.gsp26se114.chatbot_rag_be.payload.request;

import com.gsp26se114.chatbot_rag_be.entity.DocumentVisibility;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateDocumentAccessRequest(
        @NotNull(message = "Visibility không được để trống")
        DocumentVisibility visibility,
        
        List<Integer> accessibleDepartments,  // Required if visibility = SPECIFIC_DEPARTMENTS
        
        List<Integer> accessibleRoles  // Required if visibility = SPECIFIC_ROLES
) {}

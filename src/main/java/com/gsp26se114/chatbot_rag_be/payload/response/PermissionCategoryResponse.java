package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Permission Category
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PermissionCategoryResponse {
    private String category; // User Management
    private List<PermissionResponse> permissions;
}

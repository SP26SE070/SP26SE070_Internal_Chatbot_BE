package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Permission
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PermissionResponse {
    private String code; // USER_READ
    private String name; // View users
    private String description; // Xem danh sách người dùng
}

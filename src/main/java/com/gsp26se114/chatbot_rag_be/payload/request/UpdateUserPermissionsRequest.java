package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO để update permissions của user
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserPermissionsRequest {
    
    @NotNull(message = "Danh sách permissions không được null")
    private List<String> permissions; // ["DOCUMENT_READ", "DOCUMENT_WRITE", "ANALYTICS_VIEW"]
}

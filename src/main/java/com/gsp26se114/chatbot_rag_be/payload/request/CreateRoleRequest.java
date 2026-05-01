package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for creating a new custom role (by TENANT_ADMIN)
 */
public record CreateRoleRequest(
        
        @NotBlank(message = "Code không được để trống")
        @Size(min = 2, max = 50, message = "Code phải từ 2-50 ký tự")
        @Pattern(regexp = "^[A-Z_]+$", message = "Code chỉ được chứa chữ in hoa và dấu gạch dưới")
        String code,
        
        @NotBlank(message = "Name không được để trống")
        @Size(min = 2, max = 100, message = "Name phải từ 2-100 ký tự")
        String name,
        
        @Size(max = 500, message = "Description không được quá 500 ký tự")
        String description,

        @NotNull(message = "Level không được để trống")
        @Min(value = 1, message = "Level phải từ 1 đến 5")
        @Max(value = 5, message = "Level phải từ 1 đến 5")
        Integer level,
        
        @NotEmpty(message = "Permissions không được để trống")
        List<String> permissions // ["USER_READ", "DOCUMENT_WRITE", ...]
) {
}

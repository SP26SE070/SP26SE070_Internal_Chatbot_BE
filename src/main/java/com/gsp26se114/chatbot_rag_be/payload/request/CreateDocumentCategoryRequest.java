package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Tạo mới một document category.
 * parentId = null → category cấp root.
 */
public record CreateDocumentCategoryRequest(

        @NotBlank(message = "Tên category không được để trống")
        @Size(max = 200, message = "Tên category tối đa 200 ký tự")
        String name,

        @NotBlank(message = "Code không được để trống")
        @Size(max = 100, message = "Code tối đa 100 ký tự")
        String code,

        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
        String description,

        /** UUID của category cha; null = cấp root */
        UUID parentId
) {}

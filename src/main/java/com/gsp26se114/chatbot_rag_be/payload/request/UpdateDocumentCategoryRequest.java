package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Cập nhật thông tin một document category. */
public record UpdateDocumentCategoryRequest(

        @NotBlank(message = "Tên category không được để trống")
        @Size(max = 200)
        String name,

        @NotBlank(message = "Code không được để trống")
        @Size(max = 100)
        String code,

        @Size(max = 1000)
        String description,

        /** Đổi parent (null = đưa lên root) */
        UUID parentId,

        Boolean isActive
) {}

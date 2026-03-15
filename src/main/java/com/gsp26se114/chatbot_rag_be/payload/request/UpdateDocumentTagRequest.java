package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDocumentTagRequest(
        @NotBlank(message = "Tên tag không được để trống")
        @Size(max = 150, message = "Tên tag tối đa 150 ký tự")
        String name,

        @NotBlank(message = "Code không được để trống")
        @Size(max = 100, message = "Code tối đa 100 ký tự")
        String code,

        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
        String description,

        Boolean isActive
) {}
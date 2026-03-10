package com.gsp26se114.chatbot_rag_be.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Response cho một document category, hỗ trợ cấu trúc cây (children). */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentCategoryResponse {

    private UUID id;
    private UUID tenantId;
    private UUID parentId;
    private String name;
    private String code;
    private String description;
    private Boolean isActive;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Sub-categories (chỉ có giá trị khi gọi API tree, null khi flat list) */
    private List<DocumentCategoryResponse> children;
}

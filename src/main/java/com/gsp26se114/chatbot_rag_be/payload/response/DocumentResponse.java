package com.gsp26se114.chatbot_rag_be.payload.response;

import com.gsp26se114.chatbot_rag_be.entity.DocumentVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for document upload
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {
    
    private UUID id;
    private String originalFileName;
    private String fileType;
    private Long fileSize;
    private UUID categoryId;
    private String description;
    private List<DocumentTagResponse> tags;
    private DocumentVisibility visibility;
    private List<Integer> accessibleDepartments;
    private List<Integer> accessibleRoles;
    private String embeddingStatus;
    private Integer chunkCount;
    private LocalDateTime uploadedAt;

    // Display field
    private String documentTitle;
}

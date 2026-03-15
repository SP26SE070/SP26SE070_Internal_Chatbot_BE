package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeletedDocumentResponse {

    private UUID id;
    private String originalFileName;
    private String documentTitle;
    private String description;
    private LocalDateTime uploadedAt;
    private UUID deletedBy;
    private LocalDateTime deletedAt;
}

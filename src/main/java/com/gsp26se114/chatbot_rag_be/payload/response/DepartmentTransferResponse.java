package com.gsp26se114.chatbot_rag_be.payload.response;

import com.gsp26se114.chatbot_rag_be.entity.TransferRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO cho Department Transfer Request
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DepartmentTransferResponse {
    
    private UUID id;
    private UUID userId;
    private String userEmail;
    private String userDisplayName;
    private Integer currentDepartmentId;
    private Integer requestedDepartmentId;
    private String reason;
    private TransferRequestStatus status;
    private UUID reviewedBy;
    private String reviewerEmail;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.gsp26se114.chatbot_rag_be.payload.response;

import com.gsp26se114.chatbot_rag_be.entity.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for tenant information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {
    private UUID id;
    
    // Company Information
    private String name;
    private String address;
    private String website;
    private String industry;
    private String companySize;
    
    // Representative Information
    private String contactEmail;
    private String representativeName;
    private String representativePosition;
    private String representativePhone;
    
    // Request Information
    private String requestMessage;
    private LocalDateTime requestedAt;
    
    // Approval Information
    private TenantStatus status;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    
    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

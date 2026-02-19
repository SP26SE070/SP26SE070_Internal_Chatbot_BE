package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO cho phê duyệt/từ chối transfer request
 */
@Data
public class ReviewTransferRequestDTO {
    
    /**
     * Ghi chú từ người phê duyệt (bắt buộc nếu REJECT)
     */
    @Size(max = 500, message = "Ghi chú không quá 500 ký tự")
    private String reviewNote;
}

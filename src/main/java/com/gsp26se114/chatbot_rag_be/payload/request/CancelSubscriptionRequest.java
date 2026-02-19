package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for cancelling subscription
 *
 * @author GSP26SE114
 * @version 1.0
 */
@Data
public class CancelSubscriptionRequest {

    /**
     * Reason for cancellation
     */
    @NotBlank(message = "Cancellation reason is required")
    @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
    private String reason;
}

package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateMessageRequest {

    @NotNull(message = "Rating is required")
    private Short rating;

    @Size(max = 1000, message = "Feedback text must not exceed 1000 characters")
    private String feedbackText;

    @AssertTrue(message = "Rating must be 5 (helpful) or 1 (not-helpful)")
    public boolean isSupportedBinaryRating() {
        return rating == null || rating == 1 || rating == 5;
    }
}

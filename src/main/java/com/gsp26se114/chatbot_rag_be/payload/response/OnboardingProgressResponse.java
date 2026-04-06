package com.gsp26se114.chatbot_rag_be.payload.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record OnboardingProgressResponse(
        UUID moduleId,
        Integer readPercent,
        Boolean completed,
        LocalDateTime completedAt,
        LocalDateTime lastViewedAt
) {}

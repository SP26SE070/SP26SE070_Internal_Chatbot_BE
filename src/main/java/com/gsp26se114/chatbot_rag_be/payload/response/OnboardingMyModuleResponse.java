package com.gsp26se114.chatbot_rag_be.payload.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OnboardingMyModuleResponse(
        UUID id,
        String title,
        String summary,
        String content,
        Integer displayOrder,
        Integer estimatedMinutes,
        List<String> requiredPermissions,
        String detailFileName,
        String detailFileType,
        Long detailFileSize,
        Integer readPercent,
        Boolean completed,
        LocalDateTime completedAt,
        LocalDateTime lastViewedAt
) {}

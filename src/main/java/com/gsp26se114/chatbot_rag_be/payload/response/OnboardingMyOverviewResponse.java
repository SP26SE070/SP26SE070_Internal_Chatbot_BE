package com.gsp26se114.chatbot_rag_be.payload.response;

import java.util.List;

public record OnboardingMyOverviewResponse(
        Integer totalModules,
        Integer completedModules,
        Integer progressPercent,
        Boolean hasIncompleteModules,
        List<OnboardingMyModuleResponse> modules
) {}

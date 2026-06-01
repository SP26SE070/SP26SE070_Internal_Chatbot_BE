package com.gsp26se114.chatbot_rag_be.entity;

import java.util.Locale;

public enum ChatbotMode {
    STRICT,
    BALANCED,
    FLEXIBLE;

    public static ChatbotMode from(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        try {
            return ChatbotMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BALANCED;
        }
    }

    public boolean allowsAnswerWithoutContext() {
        return this != STRICT;
    }
}

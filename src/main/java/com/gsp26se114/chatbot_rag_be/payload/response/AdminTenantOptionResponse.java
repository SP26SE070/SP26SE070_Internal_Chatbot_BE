package com.gsp26se114.chatbot_rag_be.payload.response;

import java.util.UUID;

/**
 * Minimal tenant payload for admin-side filters.
 */
public record AdminTenantOptionResponse(
        UUID id,
        String name,
        String status
) {
}


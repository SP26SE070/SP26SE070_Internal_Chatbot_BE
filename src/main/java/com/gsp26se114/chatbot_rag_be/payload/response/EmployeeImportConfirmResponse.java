package com.gsp26se114.chatbot_rag_be.payload.response;

import java.util.List;
import java.util.UUID;

public record EmployeeImportConfirmResponse(
        int createdCount,
        int failedCount,
        int emailsQueued,
        List<CreatedImportUser> created,
        List<FailedImportUser> failed
) {
    public record CreatedImportUser(
            int rowNumber,
            UUID userId,
            String fullName,
            String loginEmail,
            String contactEmail
    ) {}

    public record FailedImportUser(
            int rowNumber,
            String contactEmail,
            String message
    ) {}
}

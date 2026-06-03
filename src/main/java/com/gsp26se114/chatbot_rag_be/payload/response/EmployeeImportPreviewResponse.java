package com.gsp26se114.chatbot_rag_be.payload.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EmployeeImportPreviewResponse(
        UUID importSessionId,
        LocalDateTime expiresAt,
        ImportSummary summary,
        List<ValidImportRow> validRows,
        List<InvalidImportRow> invalidRows
) {
    public record ImportSummary(int total, int valid, int invalid) {}

    public record ValidImportRow(
            int rowNumber,
            String stt,
            String fullName,
            String contactEmail,
            String phoneNumber,
            String dateOfBirth,
            String address,
            String roleCode,
            String roleName,
            String departmentCode,
            String departmentName
    ) {}

    /** Một dòng nhân viên không hợp lệ (gộp tất cả lỗi của dòng đó). */
    public record InvalidImportRow(
            int rowNumber,
            String stt,
            String fullName,
            String contactEmail,
            List<String> errors
    ) {}
}

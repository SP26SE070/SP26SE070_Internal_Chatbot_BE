package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Thông tin một phiên bản cũ của tài liệu (lịch sử version). */
@Data
@Builder
public class DocumentVersionResponse {

    private UUID versionId;
    private UUID documentId;

    /** Số phiên bản của snapshot này */
    private Integer versionNumber;

    /** Ghi chú nội dung thay đổi */
    private String versionNote;

    /** Ngày tháng phiên bản này được lưu (= ngày upload version tiếp theo) */
    private LocalDateTime createdAt;
}

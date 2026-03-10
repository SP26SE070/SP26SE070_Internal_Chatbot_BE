package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tóm tắt AI-generated cho mỗi document (1-1 với documents).
 * Được Gemini tạo tự động sau khi document được xử lý xong.
 */
@Entity
@Table(name = "document_summaries", indexes = {
    @Index(name = "idx_doc_summaries_tenant", columnList = "tenant_id")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "summary_id")
    private UUID id;

    @Column(name = "document_id", nullable = false, unique = true)
    private UUID documentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Tóm tắt nội dung chính (1-2 đoạn) */
    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    /** Các chủ đề chính: ["Onboarding", "HR Policy", "Benefits"] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_topics", columnDefinition = "jsonb")
    private List<String> keyTopics;

    @Column(length = 10)
    private String language = "vi";

    /**
     * Trạng thái generate: PENDING, PROCESSING, DONE, FAILED
     */
    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "token_used")
    private Integer tokenUsed;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

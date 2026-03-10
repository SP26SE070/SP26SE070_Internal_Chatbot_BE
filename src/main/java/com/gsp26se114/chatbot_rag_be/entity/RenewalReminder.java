package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lưu trạng thái email nhắc gia hạn subscription đã gửi.
 * Đảm bảo không gửi trùng cùng một mốc nhắc cho cùng một subscription.
 */
@Entity
@Table(name = "renewal_reminders", indexes = {
    @Index(name = "idx_renewal_reminders_subscription", columnList = "subscription_id")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RenewalReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "renewal_reminder_id")
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Mốc nhắc (số ngày trước hết hạn): 7, 3, 0
     */
    @Column(name = "remind_day", nullable = false)
    private Integer remindDay;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(name = "email_to", nullable = false)
    private String emailTo;

    /**
     * Trạng thái: SENT, FAILED, OPENED
     */
    @Column(nullable = false, length = 30)
    private String status = "SENT";

    @Column(name = "qr_content", columnDefinition = "TEXT")
    private String qrContent;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

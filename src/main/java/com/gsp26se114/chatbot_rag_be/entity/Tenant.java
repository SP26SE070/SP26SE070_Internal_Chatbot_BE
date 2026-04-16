package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tenant_id")
    private UUID id;
    
    // ========== COMPANY INFORMATION ==========
    @Column(nullable = false)
    private String name; // Tên công ty
    
    @Column(length = 500)
    private String address; // Địa chỉ công ty
    
    private String website; // Website công ty
    
    @Column(length = 50)
    private String companySize; // Quy mô: "1-50", "51-200", "201-500", "500+"
    
    // ========== REPRESENTATIVE INFORMATION ==========
    // Email người đại diện - SAU KHI APPROVE sẽ trở thành TENANT_ADMIN
    // Credentials sẽ gửi đến email này → Implicit email verification
    @Column(nullable = false, unique = true)
    private String contactEmail;
    
    @Column(length = 100)
    private String representativeName; // Họ tên người đại diện
    
    @Column(length = 100)
    private String representativePosition; // Chức vụ (CEO, CTO, HR Manager, etc.)
    
    @Column(length = 20)
    private String representativePhone; // Số điện thoại liên hệ
    
    // ========== REQUEST INFORMATION ==========
    @Column(length = 1000)
    private String requestMessage; // Lý do muốn sử dụng platform
    
    private LocalDateTime requestedAt; // Thời gian gửi request
    
    // ========== APPROVAL/REJECTION INFORMATION ==========
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status; // PENDING, ACTIVE, REJECTED, SUSPENDED
    
    @Column(name = "reviewed_by")
    private UUID reviewedBy; // UUID của SUPER_ADMIN đã review
    
    private LocalDateTime reviewedAt; // Thời gian approve/reject
    
    @Column(length = 500)
    private String rejectionReason; // Lý do reject (nếu có)
    
    // ========== SUBSCRIPTION ==========
    @Column(name = "subscription_id")
    private UUID subscriptionId; // FK to the current active Subscription

    @Column(name = "is_trial", nullable = false)
    private Boolean isTrial = false;

    @Column(name = "trial_used", nullable = false)
    private Boolean trialUsed = false;

    // ========== AUDIT FIELDS ==========
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    /** Timestamp when tenant became inactive (subscription expired) */
    @Column(name = "inactive_at")
    private LocalDateTime inactiveAt;

    /** Flag set when tenant has been inactive for 6+ months */
    @Column(name = "marked_for_deletion", nullable = false)
    private Boolean markedForDeletion = false;
}
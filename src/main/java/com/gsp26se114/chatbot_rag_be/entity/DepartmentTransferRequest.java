package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity lưu yêu cầu chuyển phòng ban (Department Transfer Request)
 * - Employee/Content Manager muốn chuyển phòng ban phải gửi request với lý do
 * - Tenant Admin hoặc người quản lý phê duyệt
 */
@Entity
@Table(name = "department_transfer_requests")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DepartmentTransferRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "department_transfer_request_id")
    private UUID id;
    
    /**
     * ID của user yêu cầu chuyển phòng
     */
    @Column(nullable = false)
    private UUID userId;
    
    /**
     * Tenant ID (để lọc request theo tenant)
     */
    @Column(nullable = false)
    private UUID tenantId;
    
    /**
     * Department ID hiện tại
     */
    private Integer currentDepartmentId;
    
    /**
     * Department ID mới muốn chuyển đến
     */
    @Column(nullable = false)
    private Integer requestedDepartmentId;
    
    /**
     * Lý do yêu cầu chuyển phòng
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String reason;
    
    /**
     * Trạng thái: PENDING, APPROVED, REJECTED
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferRequestStatus status = TransferRequestStatus.PENDING;
    
    /**
     * ID của người phê duyệt (TENANT_ADMIN hoặc manager)
     */
    private UUID reviewedBy;
    
    /**
     * Thời điểm phê duyệt
     */
    private LocalDateTime reviewedAt;
    
    /**
     * Ghi chú từ người phê duyệt (nếu reject)
     */
    @Column(columnDefinition = "TEXT")
    private String reviewNote;
    
    /**
     * Thời điểm tạo request
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * Thời điểm cập nhật
     */
    private LocalDateTime updatedAt;
}

package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.DepartmentTransferRequest;
import com.gsp26se114.chatbot_rag_be.entity.TransferRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DepartmentTransferRequestRepository extends JpaRepository<DepartmentTransferRequest, UUID> {
    
    /**
     * Lấy tất cả transfer requests của một tenant (để TENANT_ADMIN quản lý)
     */
    List<DepartmentTransferRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    
    /**
     * Lấy transfer requests theo status (ví dụ: chỉ lấy PENDING)
     */
    List<DepartmentTransferRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, TransferRequestStatus status);
    
    /**
     * Lấy transfer requests của một user cụ thể
     */
    List<DepartmentTransferRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    /**
     * Kiểm tra xem user có request PENDING nào không
     */
    boolean existsByUserIdAndStatus(UUID userId, TransferRequestStatus status);
    
    /**
     * Đếm số lượng transfer requests theo tenant và status
     */
    int countByTenantIdAndStatus(UUID tenantId, TransferRequestStatus status);
}

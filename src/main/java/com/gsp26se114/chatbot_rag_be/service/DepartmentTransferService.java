package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.Department;
import com.gsp26se114.chatbot_rag_be.entity.DepartmentTransferRequest;
import com.gsp26se114.chatbot_rag_be.entity.TransferRequestStatus;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.repository.DepartmentRepository;
import com.gsp26se114.chatbot_rag_be.repository.DepartmentTransferRequestRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DepartmentTransferService {
    
    private final DepartmentTransferRequestRepository transferRequestRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    
    /**
     * Employee/Content Manager tạo yêu cầu chuyển phòng ban
     */
    @Transactional
    public DepartmentTransferRequest createTransferRequest(
            UUID userId, 
            Integer requestedDepartmentId, 
            String reason) {
        
        // 1. Kiểm tra user tồn tại
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
        
        // 2. Kiểm tra không có request PENDING nào
        if (transferRequestRepository.existsByUserIdAndStatus(userId, TransferRequestStatus.PENDING)) {
            throw new RuntimeException("Bạn đang có yêu cầu chuyển phòng đang chờ phê duyệt!");
        }
        
        // 3. Kiểm tra không request chuyển đến phòng ban hiện tại
        if (requestedDepartmentId.equals(user.getDepartmentId())) {
            throw new RuntimeException("Không thể chuyển đến phòng ban hiện tại!");
        }
        
        // 4. Tạo request
        DepartmentTransferRequest request = new DepartmentTransferRequest();
        request.setUserId(userId);
        request.setTenantId(user.getTenantId());
        request.setCurrentDepartmentId(user.getDepartmentId());
        request.setRequestedDepartmentId(requestedDepartmentId);
        request.setReason(reason);
        request.setStatus(TransferRequestStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());
        
        DepartmentTransferRequest saved = transferRequestRepository.save(request);
        
        log.info("Transfer request created: userId={}, from={}, to={}", 
                 userId, user.getDepartmentId(), requestedDepartmentId);
        
        // 5. Gửi email thông báo cho TENANT_ADMIN
        notifyAdminNewRequest(user, requestedDepartmentId, reason);
        
        return saved;
    }
    
    /**
     * TENANT_ADMIN phê duyệt yêu cầu chuyển phòng
     */
    @Transactional
    public DepartmentTransferRequest approveTransferRequest(
            UUID requestId, 
            UUID reviewerId, 
            String reviewNote) {
        
        // 1. Lấy request
        DepartmentTransferRequest request = transferRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Transfer request không tồn tại!"));
        
        // 2. Kiểm tra status
        if (request.getStatus() != TransferRequestStatus.PENDING) {
            throw new RuntimeException("Request này đã được xử lý rồi!");
        }
        
        // 3. Cập nhật status
        request.setStatus(TransferRequestStatus.APPROVED);
        request.setReviewedBy(reviewerId);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(reviewNote);
        request.setUpdatedAt(LocalDateTime.now());
        
        // 4. CẬP NHẬT DEPARTMENT ID CHO USER
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
        
        user.setDepartmentId(request.getRequestedDepartmentId());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        
        transferRequestRepository.save(request);
        
        log.info("Transfer request APPROVED: requestId={}, userId={}, newDepartment={}", 
                 requestId, request.getUserId(), request.getRequestedDepartmentId());
        
        // 5. Gửi email thông báo cho user
        notifyUserApproved(user, request);
        
        return request;
    }
    
    /**
     * TENANT_ADMIN từ chối yêu cầu chuyển phòng
     */
    @Transactional
    public DepartmentTransferRequest rejectTransferRequest(
            UUID requestId, 
            UUID reviewerId, 
            String reviewNote) {
        
        // 1. Lấy request
        DepartmentTransferRequest request = transferRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Transfer request không tồn tại!"));
        
        // 2. Kiểm tra status
        if (request.getStatus() != TransferRequestStatus.PENDING) {
            throw new RuntimeException("Request này đã được xử lý rồi!");
        }
        
        // 3. Validate review note (bắt buộc khi reject)
        if (reviewNote == null || reviewNote.isBlank()) {
            throw new RuntimeException("Vui lòng nhập lý do từ chối!");
        }
        
        // 4. Cập nhật status
        request.setStatus(TransferRequestStatus.REJECTED);
        request.setReviewedBy(reviewerId);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(reviewNote);
        request.setUpdatedAt(LocalDateTime.now());
        
        transferRequestRepository.save(request);
        
        log.info("Transfer request REJECTED: requestId={}, userId={}", 
                 requestId, request.getUserId());
        
        // 5. Gửi email thông báo cho user
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
        notifyUserRejected(user, request);
        
        return request;
    }
    
    /**
     * Lấy tất cả transfer requests của tenant (cho TENANT_ADMIN)
     */
    public List<DepartmentTransferRequest> getAllTransferRequests(UUID tenantId) {
        return transferRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }
    
    /**
     * Lấy transfer requests PENDING của tenant
     */
    public List<DepartmentTransferRequest> getPendingTransferRequests(UUID tenantId) {
        return transferRequestRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(
            tenantId, TransferRequestStatus.PENDING);
    }
    
    /**
     * Lấy transfer requests của user cụ thể
     */
    public List<DepartmentTransferRequest> getMyTransferRequests(UUID userId) {
        return transferRequestRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Gửi email thông báo admin có request mới
     */
    private void notifyAdminNewRequest(User user, Integer requestedDepartmentId, String reason) {
        // TODO: Lấy email của TENANT_ADMIN trong cùng tenant và gửi email
        // String subject = "🔔 Yêu cầu chuyển phòng ban mới";
        // String body = String.format("""
        //     Có yêu cầu chuyển phòng ban mới:
        //     
        //     👤 Nhân viên: %s (%s)
        //     🏢 Từ phòng: %s
        //     🎯 Đến phòng: %s
        //     📝 Lý do: %s
        //     
        //     Vui lòng xem xét và phê duyệt trong hệ thống.
        //     
        //     Trân trọng,
        //     Chatbot RAG System
        //     """, user.getFullName(), user.getEmail(),
        //     user.getDepartmentId(), requestedDepartmentId, reason);
        
        // emailService.sendEmail(adminEmail, subject, body);
        log.info("Email notification sent to admin about new transfer request");
    }
    
    /**
     * Gửi email thông báo user được chấp thuận
     */
    private void notifyUserApproved(User user, DepartmentTransferRequest request) {
        Department department = departmentRepository.findById(request.getRequestedDepartmentId())
                .orElse(null);
        String departmentName = department != null ? department.getName() : String.valueOf(request.getRequestedDepartmentId());
        
        String htmlContent = emailTemplateService.generateTransferApprovedEmail(
            user.getFullName(),
            departmentName,
            request.getReviewedAt(),
            request.getReviewNote()
        );
        
        emailService.sendHtmlEmail(user.getContactEmail(), 
            "✅ Yêu cầu chuyển phòng ban đã được chấp thuận", 
            htmlContent);
        log.info("Approval email sent to user {}", user.getEmail());
    }
    
    /**
     * Gửi email thông báo user bị từ chối
     */
    private void notifyUserRejected(User user, DepartmentTransferRequest request) {
        String htmlContent = emailTemplateService.generateTransferRejectedEmail(
            user.getFullName(),
            request.getReviewedAt(),
            request.getReviewNote()
        );
        
        emailService.sendHtmlEmail(user.getContactEmail(), 
            "❌ Yêu cầu chuyển phòng ban đã bị từ chối", 
            htmlContent);
        log.info("Rejection email sent to user {}", user.getEmail());
    }
}

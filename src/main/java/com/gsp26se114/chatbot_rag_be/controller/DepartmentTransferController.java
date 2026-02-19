package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.DepartmentTransferRequest;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.payload.request.DepartmentTransferRequestDTO;
import com.gsp26se114.chatbot_rag_be.payload.request.ReviewTransferRequestDTO;
import com.gsp26se114.chatbot_rag_be.payload.response.DepartmentTransferResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.DepartmentTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/department-transfer")
@RequiredArgsConstructor
@Tag(name = "16. 🔄 Department Transfer", description = "Quản lý yêu cầu chuyển phòng ban (TENANT_ADMIN)")
public class DepartmentTransferController {
    
    private final DepartmentTransferService transferService;
    private final UserRepository userRepository;
    
    /**
     * Employee/Content Manager tạo yêu cầu chuyển phòng
     */
    @PostMapping("/request")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER', 'EMPLOYEE')")
    @Operation(summary = "Tạo yêu cầu chuyển phòng ban", 
               description = "Employee/Content Manager tạo request chuyển phòng kèm lý do")
    public ResponseEntity<MessageResponse> createTransferRequest(
            @Valid @RequestBody DepartmentTransferRequestDTO request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        transferService.createTransferRequest(
            userPrincipal.getId(),
            request.getRequestedDepartmentId(),
            request.getReason()
        );
        
        return ResponseEntity.ok(new MessageResponse(
            "Yêu cầu chuyển phòng ban đã được gửi! Vui lòng chờ phê duyệt từ quản lý."));
    }
    
    /**
     * Lấy danh sách transfer requests của user hiện tại
     */
    @GetMapping("/my-requests")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem yêu cầu chuyển phòng của bản thân")
    public ResponseEntity<List<DepartmentTransferResponse>> getMyRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        List<DepartmentTransferRequest> requests = 
            transferService.getMyTransferRequests(userPrincipal.getId());
        
        return ResponseEntity.ok(
            requests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList())
        );
    }
    
    /**
     * TENANT_ADMIN xem tất cả transfer requests của tenant
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Xem tất cả yêu cầu chuyển phòng (TENANT_ADMIN)")
    public ResponseEntity<List<DepartmentTransferResponse>> getAllRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        List<DepartmentTransferRequest> requests = 
            transferService.getAllTransferRequests(userPrincipal.getTenantId());
        
        return ResponseEntity.ok(
            requests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList())
        );
    }
    
    /**
     * TENANT_ADMIN xem transfer requests đang chờ phê duyệt
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Xem yêu cầu chờ phê duyệt (TENANT_ADMIN)")
    public ResponseEntity<List<DepartmentTransferResponse>> getPendingRequests(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        List<DepartmentTransferRequest> requests = 
            transferService.getPendingTransferRequests(userPrincipal.getTenantId());
        
        return ResponseEntity.ok(
            requests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList())
        );
    }
    
    /**
     * TENANT_ADMIN phê duyệt yêu cầu chuyển phòng
     */
    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Phê duyệt yêu cầu chuyển phòng (TENANT_ADMIN)")
    public ResponseEntity<MessageResponse> approveRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) ReviewTransferRequestDTO review,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        String reviewNote = review != null ? review.getReviewNote() : null;
        
        transferService.approveTransferRequest(
            requestId,
            userPrincipal.getId(),
            reviewNote
        );
        
        return ResponseEntity.ok(new MessageResponse(
            "Yêu cầu chuyển phòng đã được phê duyệt! DepartmentId của nhân viên đã được cập nhật."));
    }
    
    /**
     * TENANT_ADMIN từ chối yêu cầu chuyển phòng
     */
    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Từ chối yêu cầu chuyển phòng (TENANT_ADMIN)")
    public ResponseEntity<MessageResponse> rejectRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody ReviewTransferRequestDTO review,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        transferService.rejectTransferRequest(
            requestId,
            userPrincipal.getId(),
            review.getReviewNote()
        );
        
        return ResponseEntity.ok(new MessageResponse(
            "Yêu cầu chuyển phòng đã bị từ chối."));
    }
    
    /**
     * Helper: Map entity to response DTO
     */
    private DepartmentTransferResponse mapToResponse(DepartmentTransferRequest request) {
        // Lấy thông tin user
        User user = userRepository.findById(request.getUserId()).orElse(null);
        
        // Lấy thông tin reviewer (nếu có)
        User reviewer = request.getReviewedBy() != null 
            ? userRepository.findById(request.getReviewedBy()).orElse(null)
            : null;
        
        return DepartmentTransferResponse.builder()
            .id(request.getId())
            .userId(request.getUserId())
            .userEmail(user != null ? user.getEmail() : null)
            .userDisplayName(user != null ? user.getFullName() : null)
            .currentDepartmentId(request.getCurrentDepartmentId())
            .requestedDepartmentId(request.getRequestedDepartmentId())
            .reason(request.getReason())
            .status(request.getStatus())
            .reviewedBy(request.getReviewedBy())
            .reviewerEmail(reviewer != null ? reviewer.getEmail() : null)
            .reviewedAt(request.getReviewedAt())
            .reviewNote(request.getReviewNote())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .build();
    }
}

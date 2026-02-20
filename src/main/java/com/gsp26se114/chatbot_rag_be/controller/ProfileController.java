package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.Department;
import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.payload.request.ChangePasswordRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateContactEmailRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateProfileRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.VerifyContactEmailRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.UserProfileResponse;
import com.gsp26se114.chatbot_rag_be.repository.DepartmentRepository;
import com.gsp26se114.chatbot_rag_be.repository.RoleRepository;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Tag(name = "02. 👤 User Profile", description = "Quản lý thông tin cá nhân (All Users)")
public class ProfileController {
    
    private final ProfileService profileService;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final TenantRepository tenantRepository;
    
    /**
     * Lấy thông tin profile hiện tại
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem thông tin cá nhân", 
               description = "Lấy thông tin profile của user đang đăng nhập (tất cả role)")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        User user = profileService.getProfile(userPrincipal.getId());
        return ResponseEntity.ok(mapToResponse(user));
    }
    
    /**
     * Cập nhật thông tin cá nhân (phoneNumber, dateOfBirth, address)
     * Lưu ý: fullName và departmentId CHỈ TENANT_ADMIN mới đổi được
     */
    @PutMapping("/update")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật thông tin cá nhân", 
               description = "Cập nhật phoneNumber, dateOfBirth, address. FullName và departmentId phải nhờ TENANT_ADMIN sửa.")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        User user = profileService.updateProfile(
            userPrincipal.getId(),
            request.getPhoneNumber(),
            request.getDateOfBirth(),
            request.getAddress()
        );
        
        return ResponseEntity.ok(mapToResponse(user));
    }
    
    /**
     * Helper method: Map User entity to UserProfileResponse
     */
    private UserProfileResponse mapToResponse(User user) {
        RoleEntity role = user.getRoleId() != null ? 
            roleRepository.findById(user.getRoleId()).orElse(null) : null;
        Department department = user.getDepartmentId() != null ? 
            departmentRepository.findById(user.getDepartmentId()).orElse(null) : null;
        
        // Get tenant name
        String tenantName = null;
        if (user.getTenantId() != null) {
            tenantName = tenantRepository.findById(user.getTenantId())
                    .map(Tenant::getName)
                    .orElse(null);
        }
        
        return new UserProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getContactEmail(),
            user.getFullName(),
            user.getPhoneNumber(),
            user.getDateOfBirth(),
            user.getAddress(),
            role != null ? role.getName() : null,
            department != null ? department.getName() : null,
            tenantName,
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.getLastLoginAt()
        );
    }
    
    /**
     * Bước 1: Request update contact email → Gửi OTP verify
     */
    @PostMapping("/contact-email/request")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Yêu cầu cập nhật contact email", 
               description = "Gửi OTP đến email mới để xác thực")
    public ResponseEntity<MessageResponse> requestUpdateContactEmail(
            @Valid @RequestBody UpdateContactEmailRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        profileService.requestUpdateContactEmail(
            userPrincipal.getId(), 
            request.getNewContactEmail()
        );
        
        return ResponseEntity.ok(new MessageResponse(
            "OTP đã được gửi đến email mới. Vui lòng kiểm tra hộp thư và nhập OTP trong vòng 15 phút."
        ));
    }
    
    /**
     * Bước 2: Verify OTP → Cập nhật contact email
     */
    @PostMapping("/contact-email/verify")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xác thực và cập nhật contact email", 
               description = "Nhập OTP để hoàn tất việc cập nhật contact email")
    public ResponseEntity<MessageResponse> verifyAndUpdateContactEmail(
            @Valid @RequestBody VerifyContactEmailRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        profileService.verifyAndUpdateContactEmail(
            userPrincipal.getId(),
            request.getNewContactEmail(),
            request.getOtp()
        );
        
        return ResponseEntity.ok(new MessageResponse(
            "Contact email đã được cập nhật và xác thực thành công!"
        ));
    }
    
    /**
     * Đổi mật khẩu
     * - Nếu lần đầu login (mustChangePassword = true): KHÔNG cần old password
     * - Nếu đổi mật khẩu thông thường: CẦN verify old password
     */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đổi mật khẩu", 
               description = "Lần đầu login không cần old password. Lần sau phải verify old password.")
    public ResponseEntity<MessageResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        profileService.changePassword(
            userPrincipal.getId(),
            request.oldPassword(),  // record accessor method
            request.newPassword()   // record accessor method
        );
        
        return ResponseEntity.ok(new MessageResponse(
            "Mật khẩu đã được cập nhật thành công!"
        ));
    }
}

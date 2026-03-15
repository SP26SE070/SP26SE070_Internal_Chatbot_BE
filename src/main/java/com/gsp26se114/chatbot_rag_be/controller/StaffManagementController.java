package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateStaffRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.repository.RoleRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import com.gsp26se114.chatbot_rag_be.service.EmailService;
import com.gsp26se114.chatbot_rag_be.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/staff")
@RequiredArgsConstructor
@Tag(name = "03. 👥 Super Admin - Staff Management", description = "Quản lý tài khoản STAFF (SUPER_ADMIN)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class StaffManagementController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @PostMapping
    @Operation(summary = "Tạo tài khoản STAFF", 
               description = "SUPER_ADMIN tạo account cho STAFF để quản lý tenant")
    public ResponseEntity<?> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        // Check email exists
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Email đã được sử dụng"));
        }

        // Get STAFF role
        RoleEntity staffRole = roleRepository.findByCode("STAFF")
                .orElseThrow(() -> new RuntimeException("STAFF role not found"));

        // Generate temporary password
        String temporaryPassword = UserUtil.generateRandomPassword();

        // Create staff user
        User staff = new User();
        staff.setEmail(request.getEmail());
        staff.setPassword(passwordEncoder.encode(temporaryPassword));
        staff.setFullName(request.getFullName());
        staff.setPhoneNumber(request.getPhone());
        staff.setRoleId(staffRole.getId());
        staff.setMustChangePassword(true);  // Force password change on first login
        staff.setCreatedAt(LocalDateTime.now());

        User savedStaff = userRepository.save(staff);

        // Send welcome email with credentials
        try {
            emailService.sendStaffWelcome(
                savedStaff.getEmail(),
                savedStaff.getFullName(),
                temporaryPassword
            );
            log.info("SUPER_ADMIN created new STAFF account: {} - Welcome email sent", request.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to STAFF: {}", request.getEmail(), e);
        }

        return ResponseEntity.ok(new MessageResponse("Tài khoản STAFF đã được tạo thành công. Email thông tin đăng nhập đã được gửi."));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách STAFF", description = "Xem tất cả tài khoản STAFF")
    public ResponseEntity<List<User>> getAllStaff() {
        RoleEntity staffRole = roleRepository.findByCode("STAFF")
                .orElseThrow(() -> new RuntimeException("STAFF role not found"));
        
        List<User> staffUsers = userRepository.findByRoleId(staffRole.getId());
        return ResponseEntity.ok(staffUsers);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Xem chi tiết STAFF", description = "Lấy thông tin chi tiết của một STAFF")
    public ResponseEntity<User> getStaffById(@PathVariable UUID userId) {
        return userRepository.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{userId}/activate")
    @Operation(summary = "Kích hoạt tài khoản STAFF", description = "Bật lại tài khoản STAFF")
    public ResponseEntity<MessageResponse> activateStaff(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setIsActive(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Tài khoản STAFF đã được kích hoạt"));
    }

    @PutMapping("/{userId}/deactivate")
    @Operation(summary = "Vô hiệu hóa tài khoản STAFF", description = "Tạm ngưng tài khoản STAFF")
    public ResponseEntity<MessageResponse> deactivateStaff(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setIsActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Tài khoản STAFF đã bị vô hiệu hóa"));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Xóa tài khoản STAFF", description = "Xóa vĩnh viễn tài khoản STAFF (cẩn thận!)")
    public ResponseEntity<MessageResponse> deleteStaff(@PathVariable UUID userId) {
        if (!userRepository.existsById(userId)) {
            return ResponseEntity.notFound().build();
        }

        userRepository.deleteById(userId);
        return ResponseEntity.ok(new MessageResponse("Tài khoản STAFF đã được xóa"));
    }
}

package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.payload.request.*;
import com.gsp26se114.chatbot_rag_be.payload.response.*;
import com.gsp26se114.chatbot_rag_be.service.AuthService;
import com.gsp26se114.chatbot_rag_be.service.TenantRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "01. 🔐 Authentication", description = "Đăng nhập, đăng ký, quên mật khẩu (Public)")
public class AuthController {
    
    private final AuthService authService;
    private final TenantRegistrationService tenantRegistrationService;

    // 1. ĐĂNG NHẬP
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    // 2. ĐĂNG XUẤT (Blacklist Token)
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok(new MessageResponse("Đăng xuất thành công!"));
    }

    // 3. FORGOT PASSWORD
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        // Dùng req.email() vì đây là Record
        authService.forgotPassword(req.email());
        return ResponseEntity.ok(new MessageResponse("Mã OTP đã được gửi vào email của bạn."));
    }

    // 4. ĐẶT LẠI MẬT KHẨU
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(new MessageResponse("Mật khẩu đã được thay đổi thành công."));
    }

    // 5. REFRESH TOKEN
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest req) {
        return ResponseEntity.ok(authService.refreshAccessToken(req.refreshToken()));
    }

    // 6. ĐĂNG KÝ TENANT MỚI (Public - không cần login)
    @PostMapping("/register-tenant")
    @Operation(summary = "Đăng ký tổ chức mới", 
               description = "API public cho công ty đăng ký sử dụng platform. Status ban đầu: PENDING, chờ SUPER_ADMIN phê duyệt.")
    public ResponseEntity<MessageResponse> registerTenant(
            @Valid @RequestBody TenantRegistrationRequest request) {
        
        tenantRegistrationService.registerTenant(request);
        
        return ResponseEntity.ok(new MessageResponse(
            "Đăng ký thành công! Yêu cầu của bạn đang chờ xét duyệt. " +
            "Chúng tôi sẽ gửi thông báo qua email khi có kết quả."));
    }
}

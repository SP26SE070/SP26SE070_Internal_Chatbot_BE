package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.BlacklistedToken;
import com.gsp26se114.chatbot_rag_be.entity.RefreshToken;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.payload.request.*;
import com.gsp26se114.chatbot_rag_be.payload.response.JwtResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.VerifyResetOtpResponse;
import com.gsp26se114.chatbot_rag_be.repository.*;
import com.gsp26se114.chatbot_rag_be.security.jwt.JwtUtils;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;

    @Value("${jwt.refreshExpiration}") private Long refreshTokenDurationMs;

    @Transactional
    public JwtResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();
        
        // Update lastLoginAt
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        String accessToken = jwtUtils.generateJwtToken(authentication);

        // FIX HÌNH 4, 5: Dùng getId() thay vì id()
        refreshTokenRepository.deleteByUser(userRepository.findById(userDetails.getId()).get());
        RefreshToken refreshToken = createRefreshToken(userDetails.getId());

        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority()).toList();

        return new JwtResponse(
                accessToken,
                refreshToken.getToken(),
                userDetails.getId(),
                userDetails.getEmail(),
                userDetails.getTenantId(),
                roles,
                user.getMustChangePassword()  // Frontend check để bắt đổi password
        );
    }

    public void logout(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            
            // Lấy expiry date từ JWT token
            Instant expiryDate = jwtUtils.getExpiryDateFromToken(jwt);
            
            // Hash JWT thành SHA-256 (64 ký tự) thay vì lưu nguyên (300+ ký tự)
            String tokenHash = hashToken(jwt);
            
            BlacklistedToken blacklistedToken = new BlacklistedToken();
            blacklistedToken.setToken(tokenHash);
            blacklistedToken.setExpiryDate(expiryDate);
            blacklistedTokenRepository.save(blacklistedToken);
        }
    }
    
    /**
     * Hash JWT token bằng SHA-256 để rút ngắn từ 300+ ký tự xuống 64 ký tự
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private static final int OTP_VALID_MINUTES = 15;
    private static final int RESET_SESSION_VALID_MINUTES = 10;

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email không tồn tại!"));
        String otp = String.format("%06d", new Random().nextInt(999999));
        user.setResetPasswordToken(otp);
        user.setTokenExpiry(LocalDateTime.now().plusMinutes(OTP_VALID_MINUTES));
        // New OTP flow: invalidate any previous verified session
        user.setPasswordResetSessionToken(null);
        user.setPasswordResetSessionExpiry(null);
        userRepository.save(user);
        
        // Gửi OTP đến email thật (contactEmail nếu có, không thì dùng email login)
        String emailToSend = getEmailToSend(user);
        String htmlContent = emailTemplateService.generateOtpResetPasswordEmail(
            user.getFullName(), otp);
        emailService.sendHtmlEmail(emailToSend, "🔐 Xác Thực OTP - Đặt Lại Mật Khẩu", htmlContent);
        log.info("OTP reset password email sent to: {}", emailToSend);
    }
    
    /**
     * Lấy email để gửi thông báo: ưu tiên contactEmail (đã verify khi lưu DB)
     */
    private String getEmailToSend(User user) {
        // Ưu tiên gửi OTP đến contactEmail (đã verify khi lưu DB)
        // Ngược lại fallback về email đăng nhập
        if (user.getContactEmail() != null) {
            log.info("Sending OTP to contact email: {}", user.getContactEmail());
            return user.getContactEmail();
        }
        return user.getEmail();
    }

    /**
     * Bước 2: xác thực OTP đúng → cấp resetSessionToken (one-time) để gọi reset-password.
     */
    @Transactional
    public VerifyResetOtpResponse verifyResetOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email không tồn tại!"));
        if (user.getResetPasswordToken() == null || user.getTokenExpiry() == null) {
            throw new RuntimeException("Chưa có mã OTP hợp lệ. Vui lòng gửi lại OTP.");
        }
        if (user.getTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP đã hết hạn!");
        }
        if (!user.getResetPasswordToken().equals(otp.trim())) {
            throw new RuntimeException("Mã OTP không đúng!");
        }

        String sessionToken = UUID.randomUUID().toString();
        user.setPasswordResetSessionToken(sessionToken);
        user.setPasswordResetSessionExpiry(LocalDateTime.now().plusMinutes(RESET_SESSION_VALID_MINUTES));
        user.setResetPasswordToken(null);
        user.setTokenExpiry(null);
        userRepository.save(user);

        return new VerifyResetOtpResponse(
                "Xác thực OTP thành công. Bạn có thể đặt lại mật khẩu.",
                sessionToken
        );
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetSessionToken(request.resetSessionToken())
                .filter(u -> u.getPasswordResetSessionExpiry() != null
                        && u.getPasswordResetSessionExpiry().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new RuntimeException("Phiên đặt lại mật khẩu hết hạn hoặc không hợp lệ!"));
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setResetPasswordToken(null);
        user.setTokenExpiry(null);
        user.setPasswordResetSessionToken(null);
        user.setPasswordResetSessionExpiry(null);
        userRepository.save(user);
    }

    @Transactional
    public JwtResponse refreshAccessToken(String refreshTokenStr) {
        // 1. Tìm refresh token trong database
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new RuntimeException("Refresh token không hợp lệ!"));

        // 2. Kiểm tra refresh token có hết hạn không
        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RuntimeException("Refresh token đã hết hạn. Vui lòng đăng nhập lại!");
        }

        // 3. Reload user from DB so permissions/role are current (not stale Hibernate proxy cache)
        User user = userRepository.findById(refreshToken.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new RuntimeException("Account has been disabled");
        }

        // 3.5 Load role information (includes jsonb permissions)
        RoleEntity role = roleRepository.findById(user.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));


        // 4. Tạo access token mới
        UserPrincipal userPrincipal = UserPrincipal.build(user, role);
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        String newAccessToken = jwtUtils.generateJwtToken(authentication);

        // 5. Trả về response với access token mới, giữ nguyên refresh token cũ
        List<String> roles = userPrincipal.getAuthorities().stream()
                .map(item -> item.getAuthority()).toList();

        return new JwtResponse(
                newAccessToken,
                refreshTokenStr, // Giữ nguyên refresh token cũ (không rotate)
                user.getId(),
                user.getEmail(),
                userPrincipal.getTenantId(),
                roles,
                user.getMustChangePassword()  // Trả về flag này cho frontend
        );
    }

    private RefreshToken createRefreshToken(UUID userId) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(userRepository.findById(userId).get());
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshToken.setToken(UUID.randomUUID().toString());
        return refreshTokenRepository.save(refreshToken);
    }
}
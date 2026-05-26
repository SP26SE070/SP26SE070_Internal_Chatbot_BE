package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.config.TenantContext;
import com.gsp26se114.chatbot_rag_be.entity.BlacklistedToken;
import com.gsp26se114.chatbot_rag_be.entity.RefreshToken;
import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.exception.ForbiddenException;
import com.gsp26se114.chatbot_rag_be.payload.request.*;
import com.gsp26se114.chatbot_rag_be.payload.response.JwtResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.VerifyResetOtpResponse;
import com.gsp26se114.chatbot_rag_be.repository.*;
import com.gsp26se114.chatbot_rag_be.security.jwt.JwtUtils;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import java.util.List;
import java.util.Random;
import java.util.UUID;

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
    private final SubscriptionRepository subscriptionRepository;

    @Value("${jwt.refreshExpiration}") private Long refreshTokenDurationMs;

    @Transactional
    public JwtResponse login(LoginRequest loginRequest) {
        return TenantContext.withDefaultDataSource(() -> {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();

            // Update lastLoginAt.
            User user = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            // Increment token version to invalidate old tokens.
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);

            // Check subscription grace period - block non-admin employees.
            if (userDetails.getTenantId() != null) {
                com.gsp26se114.chatbot_rag_be.entity.Subscription subscription =
                        subscriptionRepository.findByTenantIdAndStatus(
                                        userDetails.getTenantId(),
                                        com.gsp26se114.chatbot_rag_be.entity.SubscriptionStatus.GRACE_PERIOD)
                                .orElse(null);

                if (subscription != null) {
                    boolean isTenantAdmin = userDetails.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_TENANT_ADMIN"));
                    if (!isTenantAdmin) {
                        throw new ForbiddenException(
                                "Gói đăng ký của công ty bạn đã hết hạn. " +
                                        "Vui lòng liên hệ quản trị viên để gia hạn."
                        );
                    }
                }
            }

            // Reload user so JWT contains the updated tokenVersion.
            User userWithUpdatedTokenVersion = userRepository.findById(userDetails.getId()).get();

            // Reload role info.
            RoleEntity role = roleRepository.findById(userWithUpdatedTokenVersion.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Role not found"));

            UserPrincipal updatedPrincipal = UserPrincipal.build(userWithUpdatedTokenVersion, role);
            UsernamePasswordAuthenticationToken updatedAuth =
                    new UsernamePasswordAuthenticationToken(updatedPrincipal, null, updatedPrincipal.getAuthorities());
            String accessToken = jwtUtils.generateJwtToken(updatedAuth);

            refreshTokenRepository.deleteByUser(userWithUpdatedTokenVersion);
            refreshTokenRepository.flush();
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
                    user.getMustChangePassword()
            );
        });
    }

    public void logout(String authHeader) {
        TenantContext.runOnDefaultDataSource(() -> {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String jwt = authHeader.substring(7);
                Instant expiryDate = jwtUtils.getExpiryDateFromToken(jwt);
                String tokenHash = hashToken(jwt);

                BlacklistedToken blacklistedToken = new BlacklistedToken();
                blacklistedToken.setToken(tokenHash);
                blacklistedToken.setExpiryDate(expiryDate);
                blacklistedTokenRepository.save(blacklistedToken);
            }
        });
    }

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
        User user = TenantContext.withDefaultDataSource(() -> {
            User u = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Email không tồn tại!"));
            String otp = String.format("%06d", new Random().nextInt(999999));
            u.setResetPasswordToken(otp);
            u.setTokenExpiry(LocalDateTime.now().plusMinutes(OTP_VALID_MINUTES));
            u.setPasswordResetSessionToken(null);
            u.setPasswordResetSessionExpiry(null);
            return userRepository.save(u);
        });

        // Email sending stays outside the datasource wrapper.
        String emailToSend = getEmailToSend(user);
        String htmlContent = emailTemplateService.generateOtpResetPasswordEmail(
                user.getFullName(), user.getResetPasswordToken());
        emailService.sendHtmlEmail(emailToSend, "🔐 Xác Thực OTP - Đặt Lại Mật Khẩu", htmlContent);
        log.info("OTP reset password email sent to: {}", emailToSend);
    }

    private String getEmailToSend(User user) {
        if (user.getContactEmail() != null) {
            log.info("Sending OTP to contact email: {}", user.getContactEmail());
            return user.getContactEmail();
        }
        return user.getEmail();
    }

    @Transactional
    public VerifyResetOtpResponse verifyResetOtp(String email, String otp) {
        return TenantContext.withDefaultDataSource(() -> {
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
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        TenantContext.runOnDefaultDataSource(() -> {
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
        });
    }

    @Transactional
    public JwtResponse refreshAccessToken(String refreshTokenStr) {
        return TenantContext.withDefaultDataSource(() -> {
            RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                    .orElseThrow(() -> new RuntimeException("Refresh token không hợp lệ!"));

            if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
                refreshTokenRepository.delete(refreshToken);
                throw new RuntimeException("Refresh token đã hết hạn. Vui lòng đăng nhập lại!");
            }

            User user = userRepository.findById(refreshToken.getUser().getId())
                    .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
            if (Boolean.FALSE.equals(user.getIsActive())) {
                throw new RuntimeException("Account has been disabled");
            }

            RoleEntity role = roleRepository.findById(user.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Role not found"));

            UserPrincipal userPrincipal = UserPrincipal.build(user, role);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
            String newAccessToken = jwtUtils.generateJwtToken(authentication);

            List<String> roles = userPrincipal.getAuthorities().stream()
                    .map(item -> item.getAuthority()).toList();

            return new JwtResponse(
                    newAccessToken,
                    refreshTokenStr,
                    user.getId(),
                    user.getEmail(),
                    userPrincipal.getTenantId(),
                    roles,
                    user.getMustChangePassword()
            );
        });
    }

    private RefreshToken createRefreshToken(UUID userId) {
        return TenantContext.withDefaultDataSource(() -> {
            RefreshToken refreshToken = new RefreshToken();
            refreshToken.setUser(userRepository.findById(userId).get());
            refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
            refreshToken.setToken(UUID.randomUUID().toString());
            return refreshTokenRepository.save(refreshToken);
        });
    }
}

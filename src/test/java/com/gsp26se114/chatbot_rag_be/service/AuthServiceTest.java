package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.exception.BadRequestException;
import com.gsp26se114.chatbot_rag_be.exception.ResourceNotFoundException;
import com.gsp26se114.chatbot_rag_be.repository.BlacklistedTokenRepository;
import com.gsp26se114.chatbot_rag_be.repository.RefreshTokenRepository;
import com.gsp26se114.chatbot_rag_be.repository.RoleRepository;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import com.gsp26se114.chatbot_rag_be.security.jwt.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtils jwtUtils;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private BlacklistedTokenRepository blacklistedTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private EmailTemplateService emailTemplateService;
    @Mock private SubscriptionRepository subscriptionRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager,
                jwtUtils,
                userRepository,
                roleRepository,
                refreshTokenRepository,
                blacklistedTokenRepository,
                passwordEncoder,
                emailService,
                emailTemplateService,
                subscriptionRepository
        );
    }

    @Test
    void forgotPasswordNonExistingEmailReturnsNotFoundException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.forgotPassword("missing@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Email không tồn tại!");

        verify(emailService, never()).sendHtmlEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void verifyResetOtpWrongOtpReturnsBadRequest() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setResetPasswordToken("123456");
        user.setTokenExpiry(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyResetOtp("user@example.com", "000000"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Mã OTP không đúng!");
    }

    @Test
    void verifyResetOtpExpiredOtpReturnsBadRequest() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setResetPasswordToken("123456");
        user.setTokenExpiry(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyResetOtp("user@example.com", "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("OTP đã hết hạn!");
    }

    @Test
    void resetPasswordInvalidSessionReturnsBadRequest() {
        UUID sessionId = UUID.randomUUID();
        when(userRepository.findByPasswordResetSessionToken(sessionId.toString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(new com.gsp26se114.chatbot_rag_be.payload.request.ResetPasswordRequest(sessionId.toString(), "NewP@ssw0rd")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phiên đặt lại mật khẩu hết hạn hoặc không hợp lệ!");
    }
}

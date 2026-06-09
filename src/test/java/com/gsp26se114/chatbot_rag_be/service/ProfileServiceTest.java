package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.exception.BadRequestException;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private EmailTemplateService emailTemplateService;
    @Mock private PasswordEncoder passwordEncoder;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(userRepository, emailService, emailTemplateService, passwordEncoder);
    }

    @Test
    void wrongOldPasswordReturnsBadRequest() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setMustChangePassword(false);
        user.setPassword("encoded-password");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongP@ss1", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> profileService.changePassword(userId, "WrongP@ss1", "NewP@ssw0rd"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Mật khẩu cũ không đúng!");
    }

    @Test
    void sameNewPasswordReturnsBadRequest() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setMustChangePassword(false);
        user.setPassword("encoded-password");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SameP@ss1", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> profileService.changePassword(userId, "SameP@ss1", "SameP@ss1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Mật khẩu mới phải khác mật khẩu cũ!");
    }

    @Test
    void missingOldPasswordForRegularAccountReturnsBadRequest() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setMustChangePassword(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> profileService.changePassword(userId, "", "NewP@ssw0rd"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Mật khẩu cũ không được để trống!");
    }
}

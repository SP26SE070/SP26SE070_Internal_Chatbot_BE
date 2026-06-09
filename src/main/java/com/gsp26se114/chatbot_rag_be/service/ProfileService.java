package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.config.TenantContext;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.exception.BadRequestException;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final PasswordEncoder passwordEncoder;

    private final Map<String, String> pendingVerifications = new ConcurrentHashMap<>();

    public User getProfile(UUID userId) {
        return TenantContext.withDefaultDataSource(() ->
                userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("User không tồn tại!"))
        );
    }

    @Transactional
    public User updateProfile(UUID userId, String phoneNumber, java.time.LocalDate dateOfBirth, String address) {
        return TenantContext.withDefaultDataSource(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User không tồn tại!"));

            if (phoneNumber != null) {
                user.setPhoneNumber(phoneNumber);
            }
            if (dateOfBirth != null) {
                user.setDateOfBirth(dateOfBirth);
            }
            if (address != null) {
                user.setAddress(address);
            }

            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("Profile updated for user {}: phoneNumber={}, dateOfBirth={}, address={}",
                    userId, phoneNumber, dateOfBirth, address);

            return user;
        });
    }

    public void requestUpdateContactEmail(UUID userId, String newContactEmail) {
        User user = TenantContext.withDefaultDataSource(() -> {
            if (userRepository.existsByContactEmail(newContactEmail)) {
                throw new RuntimeException("Email này đã được sử dụng bởi tài khoản khác!");
            }

            return userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
        });

        String otp = String.format("%06d", new Random().nextInt(999999));

        String key = userId + "_" + newContactEmail;
        pendingVerifications.put(key, otp);

        String htmlContent = emailTemplateService.generateOtpChangeContactEmail(
                user.getFullName(), otp, newContactEmail);
        emailService.sendHtmlEmail(newContactEmail,
                "📧 Xác Thực Email Mới", htmlContent);

        log.info("OTP sent to {} for user {}", newContactEmail, userId);
    }

    @Transactional
    public void verifyAndUpdateContactEmail(UUID userId, String newContactEmail, String otp) {
        String key = userId + "_" + newContactEmail;
        String storedOtp = pendingVerifications.get(key);

        if (storedOtp == null || !storedOtp.equals(otp)) {
            throw new RuntimeException("OTP không hợp lệ hoặc đã hết hạn!");
        }

        TenantContext.runOnDefaultDataSource(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User không tồn tại!"));

            user.setContactEmail(newContactEmail);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        });

        pendingVerifications.remove(key);

        log.info("Contact email updated successfully for user {}: {}", userId, newContactEmail);
    }

    @Transactional
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        TenantContext.runOnDefaultDataSource(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User không tồn tại!"));

            if (!user.getMustChangePassword()) {
                if (oldPassword == null || oldPassword.isEmpty()) {
                    throw new BadRequestException("Mật khẩu cũ không được để trống!");
                }
                if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                    throw new BadRequestException("Mật khẩu cũ không đúng!");
                }
            }

            if (oldPassword != null && oldPassword.equals(newPassword)) {
                throw new BadRequestException("Mật khẩu mới phải khác mật khẩu cũ!");
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setMustChangePassword(false);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("Password changed successfully for user {}", userId);
        });
    }
}

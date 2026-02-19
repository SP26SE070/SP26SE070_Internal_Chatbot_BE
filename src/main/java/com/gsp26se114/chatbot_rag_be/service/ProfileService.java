package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.User;
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
    
    // Lưu tạm OTP (trong production nên dùng Redis với TTL)
    private final Map<String, String> pendingVerifications = new ConcurrentHashMap<>();
    
    /**
     * Lấy thông tin profile của user hiện tại
     */
    public User getProfile(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
    }
    
    /**
     * Cập nhật thông tin cá nhân (phoneNumber, dateOfBirth, address)
     * KHÔNG bao gồm fullName - CHỈ TENANT_ADMIN mới đổi được
     * KHÔNG bao gồm departmentId - phải dùng transfer request flow
     * KHÔNG bao gồm email - phải dùng flow riêng với OTP
     */
    @Transactional
    public User updateProfile(UUID userId, String phoneNumber, java.time.LocalDate dateOfBirth, String address) {
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
    }
    
    /**
     * Bước 1: Request update contact email → Gửi OTP verify
     */
    public void requestUpdateContactEmail(UUID userId, String newContactEmail) {
        // 1. Kiểm tra email đã tồn tại chưa
        if (userRepository.existsByContactEmail(newContactEmail)) {
            throw new RuntimeException("Email này đã được sử dụng bởi tài khoản khác!");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
        
        // 2. Generate OTP
        String otp = String.format("%06d", new Random().nextInt(999999));
        
        // 3. Lưu OTP tạm (key: userId_email)
        String key = userId + "_" + newContactEmail;
        pendingVerifications.put(key, otp);
        
        // 4. Gửi OTP đến email mới với HTML template
        String htmlContent = emailTemplateService.generateOtpChangeContactEmail(
            user.getFullName(), otp, newContactEmail);
        emailService.sendHtmlEmail(newContactEmail, 
            "📧 Xác Thực Email Mới", htmlContent);
        
        log.info("OTP sent to {} for user {}", newContactEmail, userId);
    }
    
    /**
     * Bước 2: Verify OTP → Cập nhật contact email
     */
    @Transactional
    public void verifyAndUpdateContactEmail(UUID userId, String newContactEmail, String otp) {
        String key = userId + "_" + newContactEmail;
        String storedOtp = pendingVerifications.get(key);
        
        // 1. Validate OTP
        if (storedOtp == null || !storedOtp.equals(otp)) {
            throw new RuntimeException("OTP không hợp lệ hoặc đã hết hạn!");
        }
        
        // 2. Update contact email
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
        
        user.setContactEmail(newContactEmail);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        
        // 3. Xóa OTP đã dùng
        pendingVerifications.remove(key);
        
        log.info("Contact email updated successfully for user {}: {}", userId, newContactEmail);
    }
    
    /**
     * Đổi mật khẩu
     * - Nếu mustChangePassword = true: KHÔNG cần verify old password (lần đầu đổi)
     * - Nếu mustChangePassword = false: BẮT BUỘC verify old password
     */
    @Transactional
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại!"));
        
        // Nếu KHÔNG phải lần đầu đổi mật khẩu → phải verify old password
        if (!user.getMustChangePassword()) {
            if (oldPassword == null || oldPassword.isEmpty()) {
                throw new RuntimeException("Mật khẩu cũ không được để trống!");
            }
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                throw new RuntimeException("Mật khẩu cũ không đúng!");
            }
        }
        
        // Validate new password khác old password
        if (oldPassword != null && oldPassword.equals(newPassword)) {
            throw new RuntimeException("Mật khẩu mới phải khác mật khẩu cũ!");
        }
        
        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);  // Đã đổi mật khẩu rồi
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        
        log.info("Password changed successfully for user {}", userId);
    }
}

package com.gsp26se114.chatbot_rag_be.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class EmailTemplateService {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    /**
     * Load HTML template from resources
     */
    private String loadTemplate(String templateName) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/email/" + templateName);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to load email template: {}", templateName, e);
            throw new RuntimeException("Failed to load email template", e);
        }
    }
    
    /**
     * Template 1: OTP Reset Password
     */
    public String generateOtpResetPasswordEmail(String fullName, String otp) {
        String template = loadTemplate("otp-reset-password.html");
        return template
                .replace("${displayName}", fullName)
                .replace("${otp}", otp);
    }
    
    /**
     * Template 2: OTP Change Contact Email
     */
    public String generateOtpChangeContactEmail(String fullName, String otp, String newEmail) {
        String template = loadTemplate("otp-change-contact-email.html");
        return template
                .replace("${displayName}", fullName)
                .replace("${otp}", otp)
                .replace("${newEmail}", newEmail);
    }
    
    /**
     * Template 3: Department Transfer Request - Notify Admin
     */
    public String generateTransferRequestAdminEmail(
            String userName, String userEmail, 
            String currentDept, String requestedDept, String reason) {
        // This template is currently not used (commented out in DepartmentTransferService)
        // But keeping the method for future use
        return "Transfer request admin email - not implemented yet";
    }
    
    /**
     * Template 4: Department Transfer Approved - Notify User
     */
    public String generateTransferApprovedEmail(
            String userName, String newDepartment, 
            LocalDateTime reviewedAt, String reviewNote) {
        String template = loadTemplate("transfer-approved.html");
        return template
                .replace("${userName}", userName)
                .replace("${newDepartment}", newDepartment)
                .replace("${reviewedAt}", reviewedAt.format(FORMATTER))
                .replace("${reviewNote}", reviewNote != null ? reviewNote : "Không có ghi chú");
    }
    
    /**
     * Template 5: Department Transfer Rejected - Notify User
     */
    public String generateTransferRejectedEmail(
            String userName, LocalDateTime reviewedAt, String reviewNote) {
        String template = loadTemplate("transfer-rejected.html");
        return template
                .replace("${userName}", userName)
                .replace("${reviewedAt}", reviewedAt.format(FORMATTER))
                .replace("${reviewNote}", reviewNote);
    }
    
    /**
     * Template 6: Tenant Approved - Send Credentials
     */
    public String generateTenantApprovedEmail(
            String representativeName, String tenantName, 
            String loginEmail, String temporaryPassword, String contactEmail) {
        String template = loadTemplate("tenant-approved.html");
        return template
                .replace("${representativeName}", representativeName)
                .replace("${tenantName}", tenantName)
                .replace("${loginEmail}", loginEmail)
                .replace("${temporaryPassword}", temporaryPassword)
                .replace("${contactEmail}", contactEmail);
    }
}

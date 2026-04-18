package com.gsp26se114.chatbot_rag_be.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class EmailTemplateService {

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

    /**
     * Template 7: Subscription Auto-Renewal - Notify Tenant with QR Code
     */
    public String generateSubscriptionRenewalEmail(
            String tenantName,
            String tier,
            String billingCycle,
            String amount,
            String transactionCode,
            String qrImageUrl,
            String renewalDueDate,
            String expiresAt) {
        return generateSubscriptionPaymentReminderEmail(
                tenantName,
                tier,
                billingCycle,
                amount,
                transactionCode,
                qrImageUrl,
                renewalDueDate,
                expiresAt
        );
    }

    /**
     * Template 7b: Subscription Payment Reminder with ready-to-pay QR.
     */
    public String generateSubscriptionPaymentReminderEmail(
                String tenantName,
                String tier,
                String billingCycle,
                String amount,
                String transactionCode,
                String qrImageUrl,
                String renewalDueDate,
                String expiresAt) {
        String template = loadTemplate("subscription-payment-reminder.html");
        return template
                .replace("${tenantName}", tenantName)
                .replace("${tier}", tier)
                .replace("${billingCycle}", billingCycle)
                .replace("${amount}", amount)
                .replace("${transactionCode}", transactionCode)
                .replace("${qrImageUrl}", qrImageUrl != null ? qrImageUrl : "")
                .replace("${renewalDueDate}", renewalDueDate)
                .replace("${expiresAt}", expiresAt);
    }

    /**
     * Template 7c: Payment Success - notify tenant after webhook confirms payment.
     */
    public String generateSubscriptionPaymentSuccessEmail(
            String tenantName,
            String tier,
            String billingCycle,
            String amount,
            String transactionCode,
            String paidAt,
            String startDate,
            String endDate) {
        String template = loadTemplate("subscription-payment-success.html");
        return template
                .replace("${tenantName}", tenantName)
                .replace("${tier}", tier)
                .replace("${billingCycle}", billingCycle)
                .replace("${amount}", amount)
                .replace("${transactionCode}", transactionCode)
                .replace("${paidAt}", paidAt)
                .replace("${startDate}", startDate)
                .replace("${endDate}", endDate);
    }

    /**
     * Template: Staff Welcome - login credentials
     */
    public String generateStaffWelcomeEmail(String staffName, String staffEmail, String temporaryPassword) {
        String template = loadTemplate("staff-welcome.html");
        return template
                .replace("${staffName}", staffName != null ? staffName : "")
                .replace("${staffEmail}", staffEmail != null ? staffEmail : "")
                .replace("${temporaryPassword}", temporaryPassword != null ? temporaryPassword : "");
    }

    /**
     * Template 8: Tenant Registration Success (request received)
     */
    public String generateTenantRegistrationSuccessEmail(
            String companyName,
            String contactEmail,
            String representativeName,
            String representativePhone,
            String requestId) {
        String template = loadTemplate("tenant-registration-success.html");
        return template
                .replace("${companyName}", companyName != null ? companyName : "")
                .replace("${contactEmail}", contactEmail != null ? contactEmail : "")
                .replace("${representativeName}", representativeName != null ? representativeName : "")
                .replace("${representativePhone}", representativePhone != null ? representativePhone : "")
                .replace("${requestId}", requestId != null ? requestId : "");
    }

    /**
     * Template 9: Tenant Registration Rejected
     */
    public String generateTenantRejectedEmail(
            String representativeName,
            String companyName,
            String rejectionReason) {
        String template = loadTemplate("tenant-rejected.html");
        return template
                .replace("${representativeName}", representativeName != null ? representativeName : "")
                .replace("${companyName}", companyName != null ? companyName : "")
                .replace("${rejectionReason}", rejectionReason != null && !rejectionReason.isBlank()
                        ? rejectionReason
                        : "Hiện tại chúng tôi chưa thể đáp ứng yêu cầu đăng ký này.");
    }

}

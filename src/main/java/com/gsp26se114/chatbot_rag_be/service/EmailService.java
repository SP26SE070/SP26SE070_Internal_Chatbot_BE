package com.gsp26se114.chatbot_rag_be.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailTemplateService emailTemplateService;

    /**
     * Gửi OTP để reset password
     */
    public void sendOtp(String to, String otp) {
        sendHtmlEmail(to, "🔐 Xác Thực OTP - Đặt Lại Mật Khẩu", 
            "<html><body style='font-family: Arial; padding: 20px;'>" +
            "<h2 style='color: #667eea;'>Mã OTP Của Bạn</h2>" +
            "<div style='background: #f8f9fa; padding: 20px; border-radius: 8px; text-align: center;'>" +
            "<p style='font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #667eea; margin: 0;'>" + otp + "</p>" +
            "</div>" +
            "<p style='margin-top: 20px;'>⏰ Mã OTP có hiệu lực trong <strong>15 phút</strong></p>" +
            "<p style='color: #dc3545;'>⚠️ KHÔNG chia sẻ mã này với bất kỳ ai!</p>" +
            "</body></html>");
    }
    
    /**
     * Gửi email tùy chỉnh với subject và body (plain text - backward compatibility)
     * @param to Địa chỉ email người nhận
     * @param subject Tiêu đề email
     * @param body Nội dung email (plain text)
     */
    public void sendEmail(String to, String subject, String body) {
        sendHtmlEmail(to, subject, convertPlainTextToHtml(body));
    }
    
    /**
     * Gửi email HTML
     * @param to Địa chỉ email người nhận
     * @param subject Tiêu đề email
     * @param htmlContent Nội dung HTML
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML content
            helper.setFrom("noreply@chatbot-rag.com", "Chatbot RAG Platform");
            
            mailSender.send(message);
            log.info("HTML email sent successfully to: {}", to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send HTML email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
    
    /**
     * Convert plain text to basic HTML for backward compatibility
     */
    private String convertPlainTextToHtml(String plainText) {
        return "<html><body style='font-family: Arial; padding: 20px; line-height: 1.6;'>" +
               "<pre style='white-space: pre-wrap; font-family: Arial;'>" + 
               plainText.replace("<", "&lt;").replace(">", "&gt;") + 
               "</pre></body></html>";
    }
    
    /**
     * Send welcome email to new employee with login credentials
     * 
     * @param contactEmail Email thật của nhân viên (nhận thông báo)
     * @param employeeName Họ tên nhân viên
     * @param loginEmail Email ảo để đăng nhập hệ thống
     * @param temporaryPassword Mật khẩu tạm thời
      * @param role Vai trò (EMPLOYEE hoặc custom role)
     * @param department Phòng ban
     * @param tenantName Tên công ty/tổ chức
     */
    public void sendEmployeeWelcome(
            String contactEmail,
            String employeeName,
            String loginEmail,
            String temporaryPassword,
            String role,
            String department,
            String tenantName) {
        
        try {
            Context context = new Context();
            context.setVariable("employeeName", employeeName);
            context.setVariable("loginEmail", loginEmail);
            context.setVariable("temporaryPassword", temporaryPassword);
            context.setVariable("role", role);
            context.setVariable("department", department);
            context.setVariable("tenantName", tenantName);
            context.setVariable("contactEmail", contactEmail);
            
            String htmlContent = templateEngine.process("email/employee-welcome", context);
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(contactEmail);
            helper.setSubject("Chào mừng bạn tham gia " + tenantName);
            helper.setText(htmlContent, true);
            helper.setFrom("noreply@chatbot-rag.com", "Chatbot RAG Platform");
            
            mailSender.send(message);
            log.info("Employee welcome email sent successfully to: {}", contactEmail);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send employee welcome email to: {}", contactEmail, e);
            throw new RuntimeException("Failed to send welcome email", e);
        }
    }
    
    /**
     * Send welcome email to STAFF's contact email. Body contains virtual login email (staff@system.com...) and temp password.
     *
     * @param toContactEmail Email thật để nhận thông báo
     * @param staffName      Họ tên STAFF
     * @param loginEmail     Email đăng nhập ảo (staff@system.com, staff2@system.com, ...)
     * @param temporaryPassword Mật khẩu tạm
     */
    public void sendStaffWelcome(
            String toContactEmail,
            String staffName,
            String loginEmail,
            String temporaryPassword) {
        try {
            String htmlContent = emailTemplateService.generateStaffWelcomeEmail(staffName, loginEmail, temporaryPassword);
            sendHtmlEmail(toContactEmail, "Tài khoản STAFF - Chatbot RAG Platform", htmlContent);
            log.info("Staff welcome email sent to contact: {}", toContactEmail);
        } catch (Exception e) {
            log.error("Failed to send staff welcome email to: {}", toContactEmail, e);
            throw new RuntimeException("Failed to send welcome email", e);
        }
    }
    
    /**
     * Send template-based email
     * 
     * @param to Recipient email address
     * @param subject Email subject
     * @param templateName Template name (without .html, e.g., "tenant-registration-success")
     * @param variables Template variables as Map
     */
    public void sendTemplateMessage(String to, String subject, String templateName, java.util.Map<String, Object> variables) {
        try {
            Context context = new Context();
            variables.forEach(context::setVariable);
            
            String htmlContent = templateEngine.process("email/" + templateName, context);
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("noreply@chatbot-rag.com", "Chatbot RAG Platform");
            
            mailSender.send(message);
            log.info("Template email ({}) sent successfully to: {}", templateName, to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send template email ({}) to: {}", templateName, to, e);
            throw new RuntimeException("Failed to send template email", e);
        }
    }

    /**
     * Send tenant approval notification email using HTML template with placeholders.
     */
    public void sendTenantApprovalEmail(com.gsp26se114.chatbot_rag_be.entity.Tenant tenant,
                                        String loginEmail,
                                        String temporaryPassword) {
        try {
            String tenantAdminEmail = tenant.getContactEmail();

            if (tenantAdminEmail == null) {
                log.warn("No contact email found for tenant: {}", tenant.getName());
                return;
            }

            String htmlContent = emailTemplateService.generateTenantApprovedEmail(
                    tenant.getRepresentativeName(),
                    tenant.getName(),
                    loginEmail,
                    temporaryPassword,
                    tenantAdminEmail
            );

            sendHtmlEmail(
                    tenantAdminEmail,
                    "Tenant Approved - Welcome to Chatbot RAG Platform",
                    htmlContent
            );
        } catch (Exception e) {
            log.error("Failed to send tenant approval email", e);
        }
    }

    /**
     * Send tenant rejection notification email using HTML template.
     */
    public void sendTenantRejectedEmail(com.gsp26se114.chatbot_rag_be.entity.Tenant tenant) {
        try {
            String contactEmail = tenant.getContactEmail();
            if (contactEmail == null) {
                log.warn("No contact email found for tenant (rejected): {}", tenant.getName());
                return;
            }

            String htmlContent = emailTemplateService.generateTenantRejectedEmail(
                    tenant.getRepresentativeName(),
                    tenant.getName(),
                    tenant.getRejectionReason()
            );

            sendHtmlEmail(
                    contactEmail,
                    "Thông báo kết quả đăng ký Chatbot RAG Platform",
                    htmlContent
            );
        } catch (Exception e) {
            log.error("Failed to send tenant rejected email", e);
        }
    }
}


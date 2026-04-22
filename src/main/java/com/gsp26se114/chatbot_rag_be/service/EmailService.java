package com.gsp26se114.chatbot_rag_be.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailTemplateService emailTemplateService;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final String BREVO_SEND_API_URL = "https://api.brevo.com/v3/smtp/email";
    private static final Gson GSON = new Gson();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(20))
            .writeTimeout(Duration.ofSeconds(20))
            .build();

    @Value("${MAIL_PROVIDER:SMTP}")
    private String mailProvider;

    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;

    @Value("${app.mail.from:${MAIL_FROM:noreply@chatbot-rag.com}}")
    private String fromEmail;

    @Value("${app.mail.from-name:${MAIL_FROM_NAME:Chatbot RAG Platform}}")
    private String fromName;

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
        if (shouldUseBrevoApi()) {
            sendHtmlEmailViaBrevoApi(to, subject, htmlContent);
            return;
        }
        sendHtmlEmailViaSmtp(to, subject, htmlContent);
    }

    private void sendHtmlEmailViaSmtp(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML content
            helper.setFrom(fromEmail, fromName);
            
            mailSender.send(message);
            log.info("HTML email sent successfully to: {} (via SMTP)", to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send HTML email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    private void sendHtmlEmailViaBrevoApi(String to, String subject, String htmlContent) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new RuntimeException("BREVO_API_KEY is missing while MAIL_PROVIDER=BREVO_API");
        }

        JsonObject payload = new JsonObject();
        JsonObject sender = new JsonObject();
        sender.addProperty("name", fromName);
        sender.addProperty("email", fromEmail);
        payload.add("sender", sender);

        JsonArray toArray = new JsonArray();
        JsonObject toObj = new JsonObject();
        toObj.addProperty("email", to);
        toArray.add(toObj);
        payload.add("to", toArray);
        payload.addProperty("subject", subject);
        payload.addProperty("htmlContent", htmlContent);

        RequestBody requestBody = RequestBody.create(GSON.toJson(payload), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(BREVO_SEND_API_URL)
                .addHeader("api-key", brevoApiKey)
                .addHeader("accept", "application/json")
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                log.error("Brevo API send failed. status={}, body={}", response.code(), body);
                throw new RuntimeException("Failed to send email via Brevo API (status=" + response.code() + ")");
            }
            log.info("HTML email sent successfully to: {} (via Brevo API)", to);
        } catch (IOException e) {
            log.error("Failed to call Brevo API for recipient: {}", to, e);
            throw new RuntimeException("Failed to send email via Brevo API", e);
        }
    }

    private boolean shouldUseBrevoApi() {
        return "BREVO_API".equalsIgnoreCase((mailProvider == null ? "" : mailProvider).trim());
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
        
        Context context = new Context();
        context.setVariable("employeeName", employeeName);
        context.setVariable("loginEmail", loginEmail);
        context.setVariable("temporaryPassword", temporaryPassword);
        context.setVariable("role", role);
        context.setVariable("department", department);
        context.setVariable("tenantName", tenantName);
        context.setVariable("contactEmail", contactEmail);

        String htmlContent = templateEngine.process("email/employee-welcome", context);
        sendHtmlEmail(contactEmail, "Chào mừng bạn tham gia " + tenantName, htmlContent);
        log.info("Employee welcome email sent successfully to: {}", contactEmail);
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
        Context context = new Context();
        variables.forEach(context::setVariable);

        String htmlContent = templateEngine.process("email/" + templateName, context);
        sendHtmlEmail(to, subject, htmlContent);
        log.info("Template email ({}) sent successfully to: {}", templateName, to);
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


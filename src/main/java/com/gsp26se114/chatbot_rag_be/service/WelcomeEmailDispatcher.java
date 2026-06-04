package com.gsp26se114.chatbot_rag_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Gửi email chào mừng / thông báo nền — không block HTTP request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WelcomeEmailDispatcher {

    private final EmailService emailService;

    public record WelcomeEmailJob(
            String contactEmail,
            String employeeName,
            String loginEmail,
            String temporaryPassword,
            String roleName,
            String departmentName,
            String tenantName
    ) {}

    @Async("mailExecutor")
    public void sendEmployeeWelcomeAsync(WelcomeEmailJob job) {
        try {
            Thread.sleep(300);
            emailService.sendEmployeeWelcome(
                    job.contactEmail(),
                    job.employeeName(),
                    job.loginEmail(),
                    job.temporaryPassword(),
                    job.roleName(),
                    job.departmentName(),
                    job.tenantName()
            );
            log.info("Async welcome email sent to: {}", job.contactEmail());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Welcome email interrupted for: {}", job.contactEmail());
        } catch (Exception e) {
            log.error("Async welcome email failed for: {}", job.contactEmail(), e);
        }
    }
}

package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.entity.TenantStatus;
import com.gsp26se114.chatbot_rag_be.payload.request.TenantRegistrationRequest;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantRegistrationService {

    private final TenantRepository tenantRepository;
    private final EmailService emailService;

    /**
     * Handle public tenant registration request.
     * Create a Tenant in PENDING status for SUPER_ADMIN review.
     */
    @Transactional
    public void registerTenant(TenantRegistrationRequest request) {
        // Basic uniqueness checks
        if (tenantRepository.existsByName(request.getCompanyName())) {
            throw new RuntimeException("Tên công ty đã được đăng ký. Vui lòng dùng tên khác hoặc liên hệ hỗ trợ.");
        }
        if (tenantRepository.existsByContactEmail(request.getContactEmail())) {
            throw new RuntimeException("Email người đại diện đã được sử dụng để đăng ký. Vui lòng dùng email khác hoặc liên hệ hỗ trợ.");
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.getCompanyName());
        tenant.setAddress(request.getAddress());
        tenant.setWebsite(request.getWebsite());
        tenant.setCompanySize(request.getCompanySize());

        tenant.setContactEmail(request.getContactEmail());
        tenant.setRepresentativeName(request.getRepresentativeName());
        tenant.setRepresentativePosition(request.getRepresentativePosition());
        tenant.setRepresentativePhone(request.getRepresentativePhone());

        tenant.setRequestMessage(request.getRequestMessage());
        tenant.setRequestedAt(LocalDateTime.now());
        tenant.setStatus(TenantStatus.PENDING);

        Tenant saved = tenantRepository.save(tenant);
        log.info("Created tenant registration request for company={}, contactEmail={}, id={}",
                saved.getName(), saved.getContactEmail(), saved.getId());

        // Send confirmation email to representative using Thymeleaf template
        try {
            String requestId = saved.getId() != null ? saved.getId().toString() : "N/A";
            Map<String, Object> variables = new HashMap<>();
            variables.put("companyName", saved.getName());
            variables.put("contactEmail", saved.getContactEmail());
            variables.put("representativeName", saved.getRepresentativeName());
            variables.put("representativePhone", saved.getRepresentativePhone());
            variables.put("requestId", requestId);

            emailService.sendTemplateMessage(
                    saved.getContactEmail(),
                    "✅ Đã nhận yêu cầu đăng ký Chatbot RAG Platform",
                    "tenant-registration-success",
                    variables
            );
            log.info("Sent tenant registration confirmation email to {}", saved.getContactEmail());
        } catch (Exception e) {
            log.error("Failed to send tenant registration confirmation email to {}", saved.getContactEmail(), e);
        }
    }
}


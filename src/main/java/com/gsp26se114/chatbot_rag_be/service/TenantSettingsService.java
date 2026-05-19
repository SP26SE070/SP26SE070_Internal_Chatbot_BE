package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateTenantProfileRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.TenantResponse;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TenantSettingsService {

    private static final long MAX_LOGO_SIZE_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_LOGO_TYPES = Set.of(
        "image/png",
        "image/jpeg",
        "image/webp"
    );

    private final TenantRepository tenantRepository;
    private final MinioService minioService;

    @Transactional
    public TenantResponse updateTenantProfile(UUID tenantId, UpdateTenantProfileRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        if (request.getAddress() != null) {
            tenant.setAddress(request.getAddress());
        }
        if (request.getWebsite() != null) {
            tenant.setWebsite(request.getWebsite());
        }
        if (request.getCompanySize() != null) {
            tenant.setCompanySize(request.getCompanySize());
        }

        tenant.setUpdatedAt(LocalDateTime.now());
        Tenant saved = tenantRepository.save(tenant);

        log.info("Tenant profile updated: tenantId={}", tenantId);
        return toResponse(saved);
    }

    @Transactional
    public TenantResponse updateTenantLogo(UUID tenantId, MultipartFile file) {
        validateLogo(file);

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        String logoUrl = minioService.uploadTenantLogo(file, tenantId);
        tenant.setLogoUrl(logoUrl);
        tenant.setUpdatedAt(LocalDateTime.now());
        Tenant saved = tenantRepository.save(tenant);

        log.info("Tenant logo updated: tenantId={}", tenantId);
        return toResponse(saved);
    }

    private void validateLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Logo file is required");
        }
        if (file.getSize() > MAX_LOGO_SIZE_BYTES) {
            throw new RuntimeException("Logo file must be 2MB or smaller");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_LOGO_TYPES.contains(contentType)) {
            throw new RuntimeException("Unsupported logo format");
        }
    }

    private TenantResponse toResponse(Tenant tenant) {
        return TenantResponse.builder()
            .id(tenant.getId())
            .name(tenant.getName())
            .address(tenant.getAddress())
            .website(tenant.getWebsite())
            .companySize(tenant.getCompanySize())
            .logoUrl(tenant.getLogoUrl())
            .contactEmail(tenant.getContactEmail())
            .representativeName(tenant.getRepresentativeName())
            .representativePosition(tenant.getRepresentativePosition())
            .representativePhone(tenant.getRepresentativePhone())
            .requestMessage(tenant.getRequestMessage())
            .requestedAt(tenant.getRequestedAt())
            .status(tenant.getStatus())
            .reviewedBy(tenant.getReviewedBy())
            .reviewedAt(tenant.getReviewedAt())
            .rejectionReason(tenant.getRejectionReason())
            .createdAt(tenant.getCreatedAt())
            .updatedAt(tenant.getUpdatedAt())
            .build();
    }
}

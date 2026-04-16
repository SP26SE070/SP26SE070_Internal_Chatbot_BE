package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private final TenantRepository tenantRepository;

    private static final int INACTIVE_MONTHS_THRESHOLD = 6;

    /**
     * Weekly job every Sunday at 02:00 AM
     * Marks tenants inactive for 6+ months for deletion review
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    @Transactional
    public void markTenantsForDeletionReview() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusMonths(INACTIVE_MONTHS_THRESHOLD);

        List<Tenant> tenants = tenantRepository.findAll();

        int marked = 0;
        for (Tenant tenant : tenants) {
            if (tenant.getInactiveAt() != null
                    && tenant.getInactiveAt().isBefore(threshold)
                    && !Boolean.TRUE.equals(tenant.getMarkedForDeletion())) {
                tenant.setMarkedForDeletion(true);
                tenant.setUpdatedAt(LocalDateTime.now());
                tenantRepository.save(tenant);
                log.info("Marked tenant {} for deletion review (inactive since {})",
                        tenant.getId(), tenant.getInactiveAt());
                marked++;
            }
        }

        if (marked > 0) {
            log.info("Data retention job: {} tenant(s) marked for deletion review", marked);
        } else {
            log.info("Data retention job: no tenants eligible for deletion review");
        }
    }
}
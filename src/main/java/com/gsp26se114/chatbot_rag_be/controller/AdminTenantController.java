package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.payload.response.AdminTenantOptionResponse;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
@Tag(name = "06. 🔐 Super Admin - Tenant Lookup", description = "Danh sách tenants tối giản phục vụ filter/subscription UI")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminTenantController {

    private final TenantRepository tenantRepository;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách tenant cho bộ lọc",
            description = "Trả danh sách tenant tối giản, ổn định (id, name, status) để FE lọc subscriptions."
    )
    public ResponseEntity<List<AdminTenantOptionResponse>> getTenantsForAdminFilter(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<AdminTenantOptionResponse> data = tenantRepository.findAllBasicForAdminFilter().stream()
                .filter(row -> row.getId() != null && row.getName() != null)
                .map(row -> new AdminTenantOptionResponse(
                        row.getId(),
                        row.getName(),
                        row.getStatus() != null ? row.getStatus().name() : null
                ))
                .toList();

        log.info("Super admin {} fetched tenant filter list: {} rows",
                principal != null ? principal.getId() : "unknown",
                data.size());

        return ResponseEntity.ok(Objects.requireNonNullElse(data, List.of()));
    }
}


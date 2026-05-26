package com.gsp26se114.chatbot_rag_be.security.jwt;

import com.gsp26se114.chatbot_rag_be.config.TenantContext;
import com.gsp26se114.chatbot_rag_be.entity.Subscription;
import com.gsp26se114.chatbot_rag_be.entity.SubscriptionStatus;
import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.entity.TenantStatus;
import com.gsp26se114.chatbot_rag_be.security.service.UserDetailsServiceImpl;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;
    private final com.gsp26se114.chatbot_rag_be.repository.BlacklistedTokenRepository blacklistedTokenRepository;
    private final com.gsp26se114.chatbot_rag_be.repository.TenantRepository tenantRepository;
    private final com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository subscriptionRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        AuthResult authResult = null;
        try {
            String path = request.getServletPath();

            // 1. BYPASS LOGIC
            if (path.startsWith("/api/v1/auth/") || path.contains("/v3/api-docs") || path.contains("/swagger-ui")) {
                filterChain.doFilter(request, response);
                return;
            }

            // 2. Get JWT
            String jwt = parseJwt(request);

            // 3. Validate token and check blacklist/tenant/subscription on Main DB ONLY
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                // Wrap all auth/registry queries to use Main DB
                authResult = TenantContext.withDefaultDataSource(() -> {
                    try {
                        String tokenHash = hashToken(jwt);
                        if (blacklistedTokenRepository.existsByToken(tokenHash)) {
                            log.warn("Token has been blacklisted (user logged out)");
                            return AuthResult.blacklisted();
                        }

                        Integer tokenVersion = jwtUtils.getClaimFromJwtToken(jwt, "tokenVersion", Integer.class);
                        String username = jwtUtils.getUserNameFromJwtToken(jwt);
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        UserPrincipal principal = (UserPrincipal) userDetails;

                        if (tokenVersion != null && !tokenVersion.equals(principal.getTokenVersion())) {
                            log.warn("Token version mismatch - old token rejected for user {}", principal.getEmail());
                            return AuthResult.versionMismatch();
                        }

                        if (principal.getTenantId() != null) {
                            Tenant tenant = tenantRepository.findById(principal.getTenantId()).orElse(null);
                            if (tenant != null && tenant.getStatus() == TenantStatus.SUSPENDED) {
                                log.warn("Access denied - tenant {} is suspended", principal.getTenantId());
                                return AuthResult.tenantSuspended();
                            }

                            Subscription subscription = subscriptionRepository
                                    .findActiveSubscriptionByTenantId(principal.getTenantId())
                                    .orElse(null);

                            if (subscription == null) {
                                subscription = subscriptionRepository
                                        .findByTenantIdAndStatus(principal.getTenantId(),
                                                SubscriptionStatus.GRACE_PERIOD)
                                        .orElse(null);
                            }

                            if (subscription != null &&
                                    subscription.getStatus() == SubscriptionStatus.GRACE_PERIOD) {
                                boolean isTenantAdmin = "TENANT_ADMIN".equals(principal.getRoleCode());
                                if (!isTenantAdmin) {
                                    log.warn("Access denied - grace period, user {} not TENANT_ADMIN", principal.getId());
                                    return AuthResult.gracePeriodDenied();
                                }
                                log.info("Grace period access granted for TENANT_ADMIN: {}", principal.getId());
                            }
                        }

                        return AuthResult.success(userDetails, username);
                    } catch (DisabledException e) {
                        return AuthResult.disabled(e.getMessage());
                    }
                });

                // Process result outside the wrapper so response/security context can use real tenant context.
                if (authResult.status == AuthStatus.BLACKLISTED) {
                    filterChain.doFilter(request, response);
                    return;
                }
                if (authResult.status == AuthStatus.VERSION_MISMATCH) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Session expired. Please login again.\"}");
                    return;
                }
                if (authResult.status == AuthStatus.TENANT_SUSPENDED) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Tenant account is suspended\"}");
                    return;
                }
                if (authResult.status == AuthStatus.GRACE_PERIOD_DENIED) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Subscription expired. Please contact your administrator to renew.\"}");
                    return;
                }
                if (authResult.status == AuthStatus.DISABLED) {
                    log.warn("Access denied - user account is disabled: {}", authResult.errorMessage);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Account has been deactivated\"}");
                    return;
                }

                if (authResult.status == AuthStatus.SUCCESS) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            authResult.userDetails,
                            null,
                            authResult.userDetails.getAuthorities()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.info("Authenticated user: {}", authResult.username);
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        // Also support token as query parameter for file downloads.
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        return null;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private enum AuthStatus {
        SUCCESS, BLACKLISTED, VERSION_MISMATCH, TENANT_SUSPENDED,
        GRACE_PERIOD_DENIED, DISABLED
    }

    private static class AuthResult {
        final AuthStatus status;
        final UserDetails userDetails;
        final String username;
        final String errorMessage;

        private AuthResult(AuthStatus status, UserDetails userDetails, String username, String errorMessage) {
            this.status = status;
            this.userDetails = userDetails;
            this.username = username;
            this.errorMessage = errorMessage;
        }

        static AuthResult success(UserDetails userDetails, String username) {
            return new AuthResult(AuthStatus.SUCCESS, userDetails, username, null);
        }

        static AuthResult blacklisted() {
            return new AuthResult(AuthStatus.BLACKLISTED, null, null, null);
        }

        static AuthResult versionMismatch() {
            return new AuthResult(AuthStatus.VERSION_MISMATCH, null, null, null);
        }

        static AuthResult tenantSuspended() {
            return new AuthResult(AuthStatus.TENANT_SUSPENDED, null, null, null);
        }

        static AuthResult gracePeriodDenied() {
            return new AuthResult(AuthStatus.GRACE_PERIOD_DENIED, null, null, null);
        }

        static AuthResult disabled(String message) {
            return new AuthResult(AuthStatus.DISABLED, null, null, message);
        }
    }
}

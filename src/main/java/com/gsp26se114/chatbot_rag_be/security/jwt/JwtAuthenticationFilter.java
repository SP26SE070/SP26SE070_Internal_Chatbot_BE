package com.gsp26se114.chatbot_rag_be.security.jwt;

import com.gsp26se114.chatbot_rag_be.entity.Tenant;
import com.gsp26se114.chatbot_rag_be.entity.TenantStatus;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.security.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor // Sử dụng Constructor Injection thay cho @Autowired
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;
    private final com.gsp26se114.chatbot_rag_be.repository.BlacklistedTokenRepository blacklistedTokenRepository;
    private final com.gsp26se114.chatbot_rag_be.repository.TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String path = request.getServletPath();

            // 1. BYPASS LOGIC: Nếu là Login hoặc Swagger thì đi thẳng, không cần check Token
            if (path.startsWith("/api/v1/auth/") || path.contains("/v3/api-docs") || path.contains("/swagger-ui")) {
                filterChain.doFilter(request, response);
                return;
            }

            // 2. Lấy JWT từ Header
            String jwt = parseJwt(request);
            
            // 3. Kiểm tra Token có hợp lệ và KHÔNG BỊ BLACKLIST
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                // CRITICAL: Hash token trước khi check blacklist
                String tokenHash = hashToken(jwt);
                if (blacklistedTokenRepository.existsByToken(tokenHash)) {
                    log.warn("Token has been blacklisted (user logged out)");
                    filterChain.doFilter(request, response);
                    return;
                }
                
                String username = jwtUtils.getUserNameFromJwtToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 3b. Check tenant suspension status
                if (userDetails instanceof UserPrincipal principal && principal.getTenantId() != null) {
                    Tenant tenant = tenantRepository.findById(principal.getTenantId()).orElse(null);
                    if (tenant != null && tenant.getStatus() == TenantStatus.SUSPENDED) {
                        log.warn("Access denied — tenant {} is suspended", principal.getTenantId());
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"Tenant account is suspended\"}");
                        return;
                    }
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, 
                        null, 
                        userDetails.getAuthorities()
                );
                
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Lưu thông tin xác thực vào Context
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.info("Authenticated user: {}", username);
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        // 4. Cho phép request đi tiếp qua các filter khác
        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
    
    /**
     * Hash JWT token bằng SHA-256 để rút ngắn từ 300+ ký tự xuống 64 ký tự
     */
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
}
package com.gsp26se114.chatbot_rag_be.config;

import com.gsp26se114.chatbot_rag_be.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter; // Tiêm thẳng vào thay vì tạo Bean thủ công

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. Cấu hình CORS (Để ông Sỹ và ông Quân gọi API từ Frontend không bị chặn)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 2. Tắt CSRF vì mình dùng Stateless JWT
            .csrf(AbstractHttpConfigurer::disable)
            
            // 3. Xử lý Exception để trả về 401 thay vì 403 gây lú
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    String traceId = UUID.randomUUID().toString();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setHeader("X-Trace-Id", traceId);
                    response.getWriter().write(
                            "{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid token\",\"traceId\":\""
                                    + traceId
                                    + "\"}"
                    );
                })
            )

            // 4. Stateless Session
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 5. Phân quyền Endpoint
            .authorizeHttpRequests(auth -> auth
                // Cho phép Login, Forgot Password và toàn bộ auth API
                .requestMatchers("/api/auth/**").permitAll()  // FIX: Remove /v1
                .requestMatchers("/api/v1/auth/**").permitAll()  // Keep for backward compat
                // Cho phép webhook từ SePay (không cần JWT)
                .requestMatchers("/api/v1/webhooks/**").permitAll()
                // Cho phép toàn bộ tài liệu Swagger/OpenAPI
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                // Các API khác phải xác thực
                .anyRequest().authenticated()
            );

        // 6. THỨ TỰ FILTER: Cực kỳ quan trọng để dứt điểm 403
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Cấu hình CORS cho phép ngrok và local
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow all origins (localhost, ngrok, frontend URLs)
        config.setAllowedOriginPatterns(List.of("*"));
        
        // Allow all HTTP methods
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        
        // Allow all headers including ngrok headers
        config.setAllowedHeaders(List.of("*"));
        
        // Expose headers that frontend needs to read
        config.setExposedHeaders(List.of(
            "Authorization", 
            "Content-Type", 
            "X-Total-Count",
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials",
            "ETag",
            "X-Preview-Mode",
            "X-Source-Content-Type",
            "X-Trace-Id"
        ));
        
        // Allow credentials (JWT tokens, cookies)
        config.setAllowCredentials(true);
        
        // Cache preflight for 1 hour to reduce OPTIONS requests
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
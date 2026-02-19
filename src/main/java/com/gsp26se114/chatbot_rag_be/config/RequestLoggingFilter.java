package com.gsp26se114.chatbot_rag_be.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter để log requests và đảm bảo headers từ ngrok được xử lý đúng
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String method = httpRequest.getMethod();
        String uri = httpRequest.getRequestURI();
        String origin = httpRequest.getHeader("Origin");
        String host = httpRequest.getHeader("Host");
        String forwardedHost = httpRequest.getHeader("X-Forwarded-Host");
        String forwardedProto = httpRequest.getHeader("X-Forwarded-Proto");
        
        // Log request info (helpful for debugging ngrok issues)
        if (log.isDebugEnabled()) {
            log.debug("==> {} {} | Origin: {} | Host: {} | X-Forwarded-Host: {} | X-Forwarded-Proto: {}", 
                method, uri, origin, host, forwardedHost, forwardedProto);
        }
        
        // Ensure CORS headers are set for ngrok and other origins
        if (origin != null && !origin.isEmpty()) {
            httpResponse.setHeader("Access-Control-Allow-Origin", origin);
            httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
            httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD");
            httpResponse.setHeader("Access-Control-Allow-Headers", "*, ngrok-skip-browser-warning");
            httpResponse.setHeader("Access-Control-Max-Age", "3600");
            httpResponse.setHeader("Access-Control-Expose-Headers", "Authorization, Content-Type, X-Total-Count");
        }
        
        // Always add ngrok-skip-browser-warning to response to prevent ngrok interstitial page
        httpResponse.setHeader("ngrok-skip-browser-warning", "true");
        
        // Handle preflight OPTIONS request
        if ("OPTIONS".equalsIgnoreCase(method)) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        
        chain.doFilter(request, response);
        
        // Log response status
        if (log.isDebugEnabled()) {
            log.debug("<== {} {} | Status: {}", method, uri, httpResponse.getStatus());
        }
    }
}

package com.gsp26se114.chatbot_rag_be.payload.response;

import java.util.List;
import java.util.UUID;

public record JwtResponse(
    String accessToken,
    String refreshToken, 
    UUID id,
    String email,
    UUID tenantId,
    List<String> roles,
    Boolean mustChangePassword  // true = phải đổi mật khẩu lần đầu
) {}
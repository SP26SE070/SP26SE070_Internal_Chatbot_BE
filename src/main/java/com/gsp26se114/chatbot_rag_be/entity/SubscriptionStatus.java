package com.gsp26se114.chatbot_rag_be.entity;

public enum SubscriptionStatus {
    ACTIVE,      // Đang hoạt động
    EXPIRED,     // Hết hạn
    CANCELLED,   // Đã hủy
    SUSPENDED,   // Bị tạm ngưng
    GRACE_PERIOD // Trong thời gian gia hạn
}

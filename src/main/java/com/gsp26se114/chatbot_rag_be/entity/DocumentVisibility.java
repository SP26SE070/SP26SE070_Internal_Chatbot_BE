package com.gsp26se114.chatbot_rag_be.entity;

/**
 * Document visibility levels cho access control
 */
public enum DocumentVisibility {
    /**
     * Toàn công ty - Tất cả users trong tenant xem được
     */
    COMPANY_WIDE,
    
    /**
     * Chỉ specific departments - User chọn departments nào được xem
     */
    SPECIFIC_DEPARTMENTS,
    
    /**
     * Chỉ specific roles - User chọn roles nào được xem (bao gồm custom roles)
     */
    SPECIFIC_ROLES,

    /**
     * Cả department VÀ role phải khớp - User chọn departments VÀ roles nào được xem
     */
    SPECIFIC_DEPARTMENTS_AND_ROLES
}

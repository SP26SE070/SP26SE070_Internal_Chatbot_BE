package com.gsp26se114.chatbot_rag_be.entity;

/**
 * Enum định nghĩa loại role trong hệ thống
 * - SYSTEM: Roles cấp hệ thống (SUPER_ADMIN, STAFF)
 * - FIXED: Roles cố định của tenant (TENANT_ADMIN, EMPLOYEE)
 * - CUSTOM: Roles custom do TENANT_ADMIN tạo
 */
public enum RoleType {
    SYSTEM,   // SUPER_ADMIN, STAFF - Platform level roles
    FIXED,    // TENANT_ADMIN, EMPLOYEE - Pre-defined tenant roles
    CUSTOM    // Accountant, HR Manager, etc. - Created by TENANT_ADMIN
}

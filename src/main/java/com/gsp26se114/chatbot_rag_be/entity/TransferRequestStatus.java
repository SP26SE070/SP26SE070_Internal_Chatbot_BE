package com.gsp26se114.chatbot_rag_be.entity;

/**
 * Trạng thái của Department Transfer Request
 */
public enum TransferRequestStatus {
    PENDING,   // Chờ phê duyệt
    APPROVED,  // Đã chấp thuận → departmentId của user được cập nhật
    REJECTED   // Từ chối
}

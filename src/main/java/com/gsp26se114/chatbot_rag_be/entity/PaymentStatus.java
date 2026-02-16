package com.gsp26se114.chatbot_rag_be.entity;

/**
 * Enum representing payment transaction status
 *
 * @author GSP26SE114
 * @version 1.0
 */
public enum PaymentStatus {
    /**
     * Payment initiated, waiting for user to complete bank transfer
     */
    PENDING,

    /**
     * Payment completed successfully, verified by webhook
     */
    SUCCESS,

    /**
     * Payment failed or cancelled by user/gateway
     */
    FAILED,

    /**
     * Payment expired (user didn't pay within timeout period)
     */
    EXPIRED,

    /**
     * Payment cancelled by user or admin
     */
    CANCELLED
}

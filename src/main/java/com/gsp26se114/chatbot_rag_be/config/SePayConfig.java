package com.gsp26se114.chatbot_rag_be.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for SePay payment gateway integration.
 * Loads settings from application-sepay.yaml via @ConfigurationProperties.
 * 
 * @author GSP26SE114
 * @version 1.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sepay")
public class SePayConfig {

    /**
     * SePay API Token from Dashboard (Menu: Cài đặt → API Token)
     */
    private String apiToken;

    /**
     * Webhook Secret for signature verification (same as API Token for SePay)
     */
    private String webhookSecret;

    /**
     * SePay API Base URL (Production: https://my.sepay.vn/userapi)
     */
    private String baseUrl;

    /**
     * Bank account information for receiving payments
     */
    private BankConfig bank;

    /**
     * Transaction-related settings
     */
    private TransactionConfig transaction;

    /**
     * Cron job schedules for auto-renewal
     */
    private CronConfig cron;

    /**
     * Nested configuration for bank account details
     */
    @Data
    public static class BankConfig {
        /**
         * Bank account number (e.g., 02317500402)
         */
        private String accountNumber;

        /**
         * Account holder name (e.g., PHAM HONG QUAN)
         */
        private String accountName;

        /**
         * Bank code (e.g., TPB, VCB, TCB)
         */
        private String bankCode;

        /**
         * Bank display name (e.g., TPBANK, Vietcombank)
         */
        private String bankName;
    }

    /**
     * Nested configuration for transaction settings
     */
    @Data
    public static class TransactionConfig {
        /**
         * Prefix for transaction content (e.g., "THANHTOAN")
         */
        private String prefix;

        /**
         * Prefix for renewal transactions (e.g., "RENEWAL")
         */
        private String renewalPrefix;

        /**
         * Payment timeout in minutes (default: 30)
         */
        private int timeoutMinutes;

        /**
         * Frontend polling interval in seconds (default: 5)
         */
        private int pollingIntervalSeconds;
    }

    /**
     * Nested configuration for cron job schedules
     */
    @Data
    public static class CronConfig {
        /**
         * Cron expression for renewal reminder emails (default: 8:00 AM daily)
         */
        private String renewalReminder;

        /**
         * Cron expression for processing renewals (default: 1:00 AM daily)
         */
        private String processRenewals;

        /**
         * Cron expression for checking expired subscriptions (default: 2:00 AM daily)
         */
        private String checkExpired;
    }
}

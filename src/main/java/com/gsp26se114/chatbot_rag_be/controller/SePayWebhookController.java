package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.service.SePayService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for handling SePay webhook callbacks.
 * This endpoint is called by SePay when a payment transaction is completed.
 * 
 * URL to configure in SePay Dashboard:
 * - Development: https://your-ngrok-url.ngrok.io/api/v1/webhooks/sepay
 * - Production: https://your-domain.com/api/v1/webhooks/sepay
 *
 * @author GSP26SE114
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Hidden // Hide from Swagger UI (internal webhook endpoint)
public class SePayWebhookController {

    private final SePayService sePayService;

    /**
     * Handle SePay webhook callback when payment is successful.
     * 
     * SePay will send POST request with:
     * - Header: Authorization: Apikey {YOUR_API_TOKEN}
     * - Body: JSON with transaction details
     * 
     * Expected JSON body:
     * {
     *   "id": 123456,
     *   "account_number": "02317500402",
     *   "amount": 2000000,
     *   "content": "THANHTOAN STANDARD 20260129 SUB-abc123",
     *   "transaction_date": "2026-01-29 14:30:00",
     *   "bank_code": "TPB",
     *   "status": "success"
     * }
     *
     * @param authorizationHeader Authorization header from SePay (format: "Apikey {token}")
     * @param webhookData Payment data from SePay
     * @return ResponseEntity with status 200 OK if processed successfully
     */
    @PostMapping("/sepay")
    public ResponseEntity<Map<String, Object>> handleSePayWebhook(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestBody Map<String, Object> webhookData
    ) {
        log.info("Received SePay webhook: {}", webhookData);

        // Step 1: Verify webhook signature (API Token)
        if (!sePayService.verifyWebhookSignature(authorizationHeader)) {
            log.warn("Invalid webhook signature. Authorization: {}", authorizationHeader);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "success", false,
                    "message", "Invalid signature"
                ));
        }

        // Step 2: Process webhook
        boolean processed = sePayService.processWebhook(webhookData);

        if (processed) {
            log.info("Webhook processed successfully");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Webhook processed"
            ));
        } else {
            log.error("Failed to process webhook");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "success", false,
                    "message", "Failed to process webhook"
                ));
        }
    }

    /**
     * Health check endpoint for webhook URL.
     * Used to verify webhook configuration in SePay dashboard.
     */
    @GetMapping("/sepay/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "service", "SePay Webhook",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}

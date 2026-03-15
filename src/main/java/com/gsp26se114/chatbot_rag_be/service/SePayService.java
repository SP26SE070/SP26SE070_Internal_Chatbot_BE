package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.config.SePayConfig;
import com.gsp26se114.chatbot_rag_be.entity.*;
import com.gsp26se114.chatbot_rag_be.repository.PaymentTransactionRepository;
import com.gsp26se114.chatbot_rag_be.repository.SubscriptionRepository;
import com.gsp26se114.chatbot_rag_be.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for SePay payment gateway integration.
 * Handles QR code generation, webhook verification, and transaction status checks.
 *
 * @author GSP26SE114
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SePayService {

    private final SePayConfig sePayConfig;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Create a payment transaction and generate QR code for bank transfer.
     *
     * @param subscription The subscription to pay for
     * @param userId User initiating the payment
     * @return PaymentTransaction with QR code information
     */
    @Transactional
    public PaymentTransaction createPayment(Subscription subscription, UUID userId) {
        log.info("Creating payment for subscription: {}", subscription.getId());

        // Generate unique transaction code
        String transactionCode = generateTransactionCode(subscription);

        // Create payment transaction record
        PaymentTransaction payment = new PaymentTransaction();
        payment.setSubscription(subscription);
        payment.setTenantId(subscription.getTenantId());
        payment.setAmount(subscription.getPrice());
        payment.setCurrency(subscription.getCurrency());
        payment.setTransactionCode(transactionCode);
        payment.setTier(subscription.getTier());
        payment.setGateway("SEPAY");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedBy(userId);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setExpiresAt(LocalDateTime.now().plusMinutes(sePayConfig.getTransaction().getTimeoutMinutes()));

        // Generate QR code URL using VietQR (no API call needed)
        String qrImageUrl = generateQRImageUrl(transactionCode, subscription.getPrice());
        payment.setQrContent(transactionCode);
        payment.setQrImageUrl(qrImageUrl);

        // Try to call SePay API for additional features (optional, can fail)
        try {
            Map<String, Object> qrResponse = callSePayCreateTransaction(
                subscription.getPrice(),
                transactionCode
            );
            // Store additional gateway response if available
            payment.setGatewayResponse(qrResponse);
            log.info("Successfully called SePay API for transaction: {}", transactionCode);
        } catch (Exception e) {
            log.warn("SePay API call failed (non-critical): {}", e.getMessage());
            // Continue anyway since we have VietQR code
        }

        log.info("Successfully created payment with QR code: {}", qrImageUrl);

        return paymentTransactionRepository.save(payment);
    }

    /**
     * Generate unique transaction code.
     * Format: THANHTOAN {TIER} {yyyyMMdd} SUB{UUID}
     * Note: No dash before SUB to match bank transfer format
     */
    private String generateTransactionCode(Subscription subscription) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String shortUuid = subscription.getId().toString().substring(0, 8).toUpperCase();
        return String.format("%s %s %s SUB%s",
            sePayConfig.getTransaction().getPrefix(),
            subscription.getTier().name(),
            dateStr,
            shortUuid
        );
    }

    /**
     * Call SePay API to create transaction and get QR code.
     * API Endpoint: POST https://my.sepay.vn/userapi/transactions/create
     */
    private Map<String, Object> callSePayCreateTransaction(BigDecimal amount, String content) {
        log.info("Calling SePay API to create transaction. Amount: {}, Content: {}", amount, content);

        String url = sePayConfig.getBaseUrl() + "/transactions/create";

        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("account_number", sePayConfig.getBank().getAccountNumber());
        requestBody.put("account_name", sePayConfig.getBank().getAccountName());
        requestBody.put("bank_code", sePayConfig.getBank().getBankCode());
        requestBody.put("amount", amount.intValue());
        requestBody.put("content", content);

        // Build headers with API Token
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Apikey " + sePayConfig.getApiToken());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (Object) restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                log.info("SePay API response: {}", body);

                // Build QR code response
                Map<String, Object> qrData = new HashMap<>();
                qrData.put("qr_content", content);
                qrData.put("qr_image_url", generateQRImageUrl(content, amount));
                qrData.put("bank_account", sePayConfig.getBank().getAccountNumber());
                qrData.put("bank_name", sePayConfig.getBank().getBankName());
                qrData.put("account_name", sePayConfig.getBank().getAccountName());
                qrData.put("amount", amount);
                qrData.put("sepay_response", body);

                return qrData;
            } else {
                throw new RuntimeException("SePay API returned non-OK status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling SePay API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create SePay transaction: " + e.getMessage());
        }
    }

    /**
     * Generate QR code image URL using VietQR API
     * Format: https://img.vietqr.io/image/{BANK_CODE}-{ACCOUNT_NUMBER}-compact2.jpg?amount={AMOUNT}&addInfo={CONTENT}
     */
    private String generateQRImageUrl(String content, BigDecimal amount) {
        String bankCode = sePayConfig.getBank().getBankCode();
        String accountNumber = sePayConfig.getBank().getAccountNumber();
        
        return String.format(
            "https://img.vietqr.io/image/%s-%s-compact2.jpg?amount=%d&addInfo=%s&accountName=%s",
            bankCode,
            accountNumber,
            amount.intValue(),
            content.replace(" ", "%20"),
            sePayConfig.getBank().getAccountName().replace(" ", "%20")
        );
    }

    /**
     * Verify webhook signature from SePay.
     * SePay uses API Token in Authorization header for verification.
     *
     * @param authorizationHeader Authorization header from webhook request
     * @return true if valid, false otherwise
     */
    public boolean verifyWebhookSignature(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Apikey ")) {
            log.warn("Invalid authorization header format");
            return false;
        }

        String providedToken = authorizationHeader.substring(7); // Remove "Apikey " prefix
        boolean isValid = providedToken.equals(sePayConfig.getApiToken());

        if (!isValid) {
            log.warn("Webhook signature verification failed");
        }

        return isValid;
    }

    /**
     * Process webhook callback from SePay when payment is successful.
     *
     * @param webhookData Payment data from SePay webhook
     * @return true if processed successfully, false otherwise
     */
    @Transactional
    public boolean processWebhook(Map<String, Object> webhookData) {
        log.info("Processing SePay webhook: {}", webhookData);

        try {
            // Extract transaction content from webhook
            String content = (String) webhookData.get("content");
            if (content == null) {
                log.error("Webhook missing 'content' field");
                return false;
            }

            // Normalize content for comparison (remove spaces, dashes, convert to uppercase)
            String normalizedContent = normalizeTransactionCode(content);
            log.info("Normalized webhook content: {} -> {}", content, normalizedContent);

            // Find payment by transaction code (with normalization)
            PaymentTransaction payment = findPaymentByNormalizedCode(normalizedContent);

            if (payment == null) {
                log.warn("Payment not found for transaction code: {} (normalized: {})", content, normalizedContent);
                return false;
            }

            // Check if already processed
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                log.info("Payment already processed: {}", payment.getId());
                return true;
            }

            // Mark payment as successful
            String gatewayTxnId = String.valueOf(webhookData.get("id"));
            payment.markAsPaid(gatewayTxnId, webhookData);
            payment.setLastWebhookAt(LocalDateTime.now());
            payment.setWebhookRetryCount(payment.getWebhookRetryCount() + 1);
            paymentTransactionRepository.save(payment);

            // Activate subscription
            Subscription subscription = payment.getSubscription();
            UUID tenantId = subscription.getTenantId();
            
            // CRITICAL: Deactivate ALL existing active subscriptions for this tenant
            // This prevents having multiple active subscriptions (e.g., TRIAL + STARTER)
            List<Subscription> existingActive = subscriptionRepository.findAllByTenantIdAndStatus(
                tenantId, SubscriptionStatus.ACTIVE
            );
            
            for (Subscription existing : existingActive) {
                if (!existing.getId().equals(subscription.getId())) {
                    log.info("Deactivating old subscription: {} (tier: {}) for tenant: {}", 
                        existing.getId(), existing.getTier(), tenantId);
                    existing.setStatus(SubscriptionStatus.CANCELLED);
                    existing.setCancelledAt(LocalDateTime.now());
                    existing.setCancellationReason("Replaced by paid subscription: " + subscription.getTier());
                    existing.setUpdatedAt(LocalDateTime.now());
                    subscriptionRepository.save(existing);
                }
            }
            
            // Now activate the new subscription
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setLastPaymentId(payment.getId().toString());
            subscription.setLastPaymentDate(LocalDateTime.now());
            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);

            tenantRepository.findById(tenantId).ifPresent(tenant -> {
                tenant.setSubscriptionId(subscription.getId());
                tenant.setIsTrial(false);
                tenant.setUpdatedAt(LocalDateTime.now());
                tenantRepository.save(tenant);
            });

            log.info("Successfully processed payment: {}, Activated subscription: {} for tenant: {}", 
                payment.getId(), subscription.getId(), tenantId);

            return true;

        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check payment status by transaction code.
     * Used for polling by frontend.
     */
    public PaymentTransaction getPaymentStatus(String transactionCode) {
        return paymentTransactionRepository.findByTransactionCode(transactionCode)
            .orElseThrow(() -> new RuntimeException("Payment not found: " + transactionCode));
    }

    /**
     * Check payment status by ID.
     */
    public PaymentTransaction getPaymentById(UUID paymentId) {
        return paymentTransactionRepository.findById(paymentId)
            .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
    }

    /**
     * Normalize transaction code for flexible matching.
     * Removes spaces, dashes, and converts to uppercase.
     * Example: "THANHTOAN STARTER 20260129 SUB-CD778D31" -> "THANHTOانSTARTER20260129SUBCD778D31"
     */
    private String normalizeTransactionCode(String code) {
        if (code == null) return "";
        return code.toUpperCase()
                   .replaceAll("[\\s\\-_]", "");  // Remove spaces, dashes, underscores
    }

    /**
     * Find payment by normalized transaction code.
     * Compares normalized versions to handle bank formatting differences.
     * Uses substring matching because banks may append reference numbers.
     */
    private PaymentTransaction findPaymentByNormalizedCode(String normalizedSearchCode) {
        // Get all pending or success payments
        List<PaymentTransaction> allPayments = paymentTransactionRepository.findAll();
        
        for (PaymentTransaction payment : allPayments) {
            String normalizedDbCode = normalizeTransactionCode(payment.getTransactionCode());
            
            // Check if DB code is contained in webhook content (bank may add reference numbers)
            if (normalizedSearchCode.contains(normalizedDbCode)) {
                log.info("Found payment match: DB={} (normalized: {}) is contained in Webhook={}", 
                         payment.getTransactionCode(), normalizedDbCode, normalizedSearchCode);
                return payment;
            }
        }
        
        return null;
    }

    /**
     * Mark expired pending payments as EXPIRED.
     * Should be called by scheduled job.
     */
    @Transactional
    public int markExpiredPayments() {
        List<PaymentTransaction> expiredPayments = paymentTransactionRepository
            .findExpiredPendingPayments(LocalDateTime.now());

        for (PaymentTransaction payment : expiredPayments) {
            payment.markAsExpired();
            log.info("Marked payment as expired: {}", payment.getId());
        }

        paymentTransactionRepository.saveAll(expiredPayments);
        return expiredPayments.size();
    }
    
    /**
     * Get payment history for a tenant.
     */
    public List<PaymentTransaction> getPaymentHistory(UUID tenantId) {
        return paymentTransactionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}

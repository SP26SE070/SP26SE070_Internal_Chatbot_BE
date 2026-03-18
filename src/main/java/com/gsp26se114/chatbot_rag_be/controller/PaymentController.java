package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.entity.PaymentTransaction;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import com.gsp26se114.chatbot_rag_be.service.SePayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for payment-related operations.
 * Handles payment status polling and transaction queries.
 *
 * @author GSP26SE114
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Tag(name = "17. 💳 Payment Management", description = "Quản lý thanh toán và giao dịch")
public class PaymentController {

    private final SePayService sePayService;

    /**
     * Get payment status by payment ID.
     * Used by frontend to poll payment status after QR code is displayed.
     *
     * Frontend should poll this endpoint every 5 seconds until status changes from PENDING.
     *
     * @param paymentId Payment transaction ID
     * @return Payment status and details
     */
    @GetMapping("/status/{paymentId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    @Operation(
        summary = "🔍 Get Payment Status",
        description = """
            Get current payment status by payment ID.
            
            **Use Case:**
            - Frontend polls this endpoint every 5 seconds after displaying QR code
            - Check if payment has been completed (status changed from PENDING to SUCCESS)
            - Get payment details including QR code image URL
            
            **Status Values:**
            - `PENDING`: Awaiting payment (user needs to scan QR and transfer)
            - `SUCCESS`: Payment completed and verified by webhook
            - `FAILED`: Payment failed or cancelled
            - `EXPIRED`: Payment timeout (30 minutes)
            
            **Polling Example:**
            ```javascript
            const pollPayment = async (paymentId) => {
              const interval = setInterval(async () => {
                const response = await fetch(`/api/v1/payment/status/{paymentId}`);
                const data = await response.json();
                
                if (data.status !== 'PENDING') {
                  clearInterval(interval);
                  if (data.status === 'SUCCESS') {
                    showSuccessMessage();
                  } else {
                    showErrorMessage();
                  }
                }
              }, 5000); // Poll every 5 seconds
            };
            ```
            
            **Response Fields:**
            - `payment_id`: UUID of payment transaction
            - `status`: Current payment status (PENDING/SUCCESS/FAILED/EXPIRED)
            - `amount`: Payment amount in VND
            - `qr_image_url`: QR code image URL for bank transfer
            - `expires_at`: Payment expiry time (ISO 8601)
            - `transaction_code`: Unique transaction code
            - `is_expired`: Boolean indicating if payment has expired
            """,
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Payment status retrieved successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
        }
    )
    public ResponseEntity<Map<String, Object>> getPaymentStatus(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Checking payment status for ID: {} by tenant: {}",
            paymentId, userPrincipal.getTenantId());

        try {
            PaymentTransaction payment = sePayService.getPaymentById(paymentId);

            // Ownership check — TENANT_ADMIN can only view their own payments
            // SUPER_ADMIN can view all payments
            boolean isSuperAdmin = userPrincipal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

            if (!isSuperAdmin &&
                !payment.getTenantId().equals(userPrincipal.getTenantId())) {
                log.warn("Tenant {} attempted to access payment {} of tenant {}",
                    userPrincipal.getTenantId(), paymentId, payment.getTenantId());
                return ResponseEntity.status(403).build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("payment_id", payment.getId());
            response.put("status", payment.getStatus().name());
            response.put("amount", payment.getAmount());
            response.put("currency", payment.getCurrency());
            response.put("transaction_code", payment.getTransactionCode());
            response.put("tier", payment.getTier().name());
            response.put("qr_image_url", payment.getQrImageUrl());
            response.put("qr_content", payment.getQrContent());
            response.put("expires_at", payment.getExpiresAt());
            response.put("created_at", payment.getCreatedAt());
            response.put("paid_at", payment.getPaidAt());
            response.put("is_expired", payment.isExpired());
            response.put("is_active", payment.isActive());

            if (payment.getErrorMessage() != null) {
                response.put("error_message", payment.getErrorMessage());
            }

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Payment not found: {}", paymentId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get payment status by transaction code.
     * Alternative endpoint using transaction code instead of payment ID.
     *
     * @param transactionCode Unique transaction code (e.g., "THANHTOAN STANDARD 20260129 SUB-abc123")
     * @return Payment status and details
     */
    @GetMapping("/status/code/{transactionCode}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    @Operation(
        summary = "🔍 Get Payment Status by Transaction Code",
        description = """
            Get payment status using transaction code instead of payment ID.
            
            **Use Case:**
            - Check payment status when only transaction code is known
            - Useful for customer support or manual verification
            
            **Transaction Code Format:**
            `THANHTOAN {TIER} {yyyyMMdd} SUB-{UUID}`
            
            Example: `THANHTOAN STANDARD 20260129 SUB-ABC12345`
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "Payment status retrieved"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
        }
    )
    public ResponseEntity<Map<String, Object>> getPaymentByCode(@PathVariable String transactionCode) {
        log.info("Checking payment status for transaction code: {}", transactionCode);

        try {
            PaymentTransaction payment = sePayService.getPaymentStatus(transactionCode);

            Map<String, Object> response = new HashMap<>();
            response.put("payment_id", payment.getId());
            response.put("status", payment.getStatus().name());
            response.put("amount", payment.getAmount());
            response.put("transaction_code", payment.getTransactionCode());
            response.put("qr_image_url", payment.getQrImageUrl());
            response.put("expires_at", payment.getExpiresAt());
            response.put("is_expired", payment.isExpired());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Payment not found for transaction code: {}", transactionCode);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Get payment history for current tenant.
     *
     * @param userPrincipal Authenticated user
     * @return List of all payment transactions for tenant
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    @Operation(
        summary = "📜 Get Payment History",
        description = """
            Get all payment transactions for current tenant.
            
            **Use Case:**
            - View payment history
            - Track subscription payments
            - Audit payment records
            
            **Response includes:**
            - All payment statuses (SUCCESS, PENDING, FAILED, EXPIRED)
            - Ordered by creation date (newest first)
            - Complete payment details including amounts and dates
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "Payment history retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
        }
    )
    public ResponseEntity<List<Map<String, Object>>> getPaymentHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Getting payment history for tenant: {}", userPrincipal.getTenantId());

        List<PaymentTransaction> payments = sePayService.getPaymentHistory(userPrincipal.getTenantId());

        List<Map<String, Object>> response = payments.stream().map(payment -> {
            Map<String, Object> paymentData = new HashMap<>();
            paymentData.put("payment_id", payment.getId());
            paymentData.put("status", payment.getStatus().name());
            paymentData.put("amount", payment.getAmount());
            paymentData.put("currency", payment.getCurrency());
            paymentData.put("transaction_code", payment.getTransactionCode());
            paymentData.put("tier", payment.getTier().name());
            paymentData.put("created_at", payment.getCreatedAt());
            paymentData.put("paid_at", payment.getPaidAt());
            paymentData.put("expires_at", payment.getExpiresAt());
            paymentData.put("is_expired", payment.isExpired());
            return paymentData;
        }).collect(Collectors.toList());

        log.info("Found {} payment transactions", payments.size());
        return ResponseEntity.ok(response);
    }
}

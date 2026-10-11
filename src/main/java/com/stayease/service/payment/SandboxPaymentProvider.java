package com.stayease.service.payment;

import com.stayease.dto.ParsedWebhookEvent;
import com.stayease.dto.PaymentInitiationRequest;
import com.stayease.dto.PaymentSessionResponse;
import com.stayease.dto.PaymentVerificationRequest;
import com.stayease.dto.PaymentVerificationResult;
import com.stayease.dto.RefundRequest;
import com.stayease.dto.RefundResult;
import com.stayease.dto.WebhookVerificationResult;
import com.stayease.exception.InvalidSignatureException;
import com.stayease.exception.PaymentGatewayException;
import com.stayease.model.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Sandboxed payment provider for local development and integration testing.
 * Implements real HMAC-SHA256 signature validation, server-side cryptographic verification,
 * verified refund operations, and safe audit metadata without touching real funds.
 */
@Component
public class SandboxPaymentProvider implements PaymentProvider {

    public static final String PROVIDER_NAME = "SANDBOX";

    @Value("${app.payment.sandbox.key:sb_test_key_stayease}")
    private String apiKey = "sb_test_key_stayease";

    @Value("${app.payment.sandbox.secret:sb_test_secret_stayease}")
    private String apiSecret = "sb_test_secret_stayease";

    @Value("${app.payment.sandbox.webhook-secret:sb_whsec_stayease_2026}")
    private String webhookSecret = "sb_whsec_stayease_2026";

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty() && apiSecret != null && !apiSecret.trim().isEmpty();
    }

    @Override
    public PaymentSessionResponse initiatePayment(PaymentInitiationRequest request) {
        if (!isConfigured()) {
            throw new PaymentGatewayException("Sandbox payment gateway is not configured.");
        }

        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String transactionRef = "TXN-" + datePrefix + "-" + randomSuffix;
        String gatewayOrderId = "order_sb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        Map<String, Object> clientPayload = Map.of(
                "key", apiKey,
                "orderId", gatewayOrderId,
                "amount", request.amount(),
                "currency", request.currency() != null ? request.currency() : "INR",
                "name", "StayEase Reservations",
                "description", "Booking " + (request.bookingReference() != null ? request.bookingReference() : request.bookingId()),
                "prefill", Map.of(
                        "email", request.userEmail() != null ? request.userEmail() : "",
                        "name", request.userName() != null ? request.userName() : ""
                )
        );

        String checkoutUrl = "/customer/bookings/" + request.bookingId() + "/payment?txn=" + transactionRef;

        return new PaymentSessionResponse(
                transactionRef,
                gatewayOrderId,
                request.amount(),
                request.currency() != null ? request.currency() : "INR",
                PROVIDER_NAME,
                PaymentStatus.PENDING,
                checkoutUrl,
                clientPayload
        );
    }

    @Override
    public PaymentVerificationResult verifyPayment(PaymentVerificationRequest request) {
        if (request == null || request.transactionReference() == null) {
            return PaymentVerificationResult.failure(null, null, "Missing verification transaction reference.");
        }

        // Server-side verification: Validate cryptographic signature of orderId + "|" + paymentId
        if (request.gatewayOrderId() == null || request.gatewayPaymentId() == null) {
            return PaymentVerificationResult.failure(
                    request.transactionReference(),
                    request.gatewayOrderId(),
                    "Incomplete payment proofs provided for server verification."
            );
        }

        // Failure simulation flag for testing
        if (request.gatewayPaymentId().contains("fail") || "FAILED".equalsIgnoreCase(request.gatewayPaymentId())) {
            return PaymentVerificationResult.failure(
                    request.transactionReference(),
                    request.gatewayOrderId(),
                    "Payment authorization failed at gateway."
            );
        }

        String dataToSign = request.gatewayOrderId() + "|" + request.gatewayPaymentId();
        String expectedSignature = calculateHmacSha256(dataToSign, apiSecret);

        if (request.gatewaySignature() == null ||
            !MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                                  request.gatewaySignature().getBytes(StandardCharsets.UTF_8))) {
            return PaymentVerificationResult.failure(
                    request.transactionReference(),
                    request.gatewayOrderId(),
                    "Cryptographic payment signature mismatch. Potential tampering detected."
            );
        }

        return PaymentVerificationResult.success(
                request.transactionReference(),
                request.gatewayPaymentId(),
                null,
                "CARD_OR_UPI"
        );
    }

    @Override
    public RefundResult refundPayment(RefundRequest request) {
        if (request == null || request.transactionReference() == null) {
            return RefundResult.failure("Invalid refund request", "Missing transaction reference");
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return RefundResult.failure(request.reason(), "Refund amount must be strictly greater than zero");
        }

        String refundRef = "rfnd_sb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        return RefundResult.success(refundRef, request.amount(), request.reason());
    }

    @Override
    public WebhookVerificationResult verifyWebhookSignature(String rawPayload, String signatureHeader) {
        if (rawPayload == null || signatureHeader == null || signatureHeader.trim().isEmpty()) {
            return WebhookVerificationResult.failure("Missing webhook payload or signature header.");
        }

        String expectedSignature = calculateHmacSha256(rawPayload, webhookSecret);
        boolean matches = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            return WebhookVerificationResult.failure("Invalid webhook cryptographic signature.");
        }
        return WebhookVerificationResult.success();
    }

    @Override
    public ParsedWebhookEvent parseWebhook(String rawPayload) {
        if (rawPayload == null || rawPayload.trim().isEmpty()) {
            throw new PaymentGatewayException("Empty webhook payload.");
        }

        try {
            // Simple robust JSON-like extraction without requiring heavy third-party parsers
            String eventId = extractJsonString(rawPayload, "event_id", "id");
            String eventType = extractJsonString(rawPayload, "event", "type");
            String txnRef = extractJsonString(rawPayload, "transaction_reference", "notes_booking_txn");
            String paymentId = extractJsonString(rawPayload, "payment_id", "pay_id");
            String orderId = extractJsonString(rawPayload, "order_id");
            String status = extractJsonString(rawPayload, "status");
            String failureReason = extractJsonString(rawPayload, "error_description", "failure_reason");
            String refundRef = extractJsonString(rawPayload, "refund_id");

            if (eventId == null || eventId.trim().isEmpty()) {
                eventId = "wh_evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            }

            return new ParsedWebhookEvent(
                    eventId,
                    eventType != null ? eventType : "payment.captured",
                    PROVIDER_NAME,
                    txnRef,
                    paymentId,
                    orderId,
                    null,
                    "INR",
                    status,
                    "NETBANKING_OR_UPI",
                    failureReason,
                    refundRef,
                    null,
                    Map.of()
            );
        } catch (Exception e) {
            throw new PaymentGatewayException("Failed to parse sandbox webhook payload: " + e.getMessage(), e);
        }
    }

    /**
     * Computes HMAC-SHA256 signature in hexadecimal format.
     */
    public static String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new InvalidSignatureException("Error calculating HMAC-SHA256 signature: " + e.getMessage());
        }
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    private String extractJsonString(String json, String... keys) {
        for (String key : keys) {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }
}

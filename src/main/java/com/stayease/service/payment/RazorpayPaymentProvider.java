package com.stayease.service.payment;

import com.stayease.dto.ParsedWebhookEvent;
import com.stayease.dto.PaymentInitiationRequest;
import com.stayease.dto.PaymentSessionResponse;
import com.stayease.dto.PaymentVerificationRequest;
import com.stayease.dto.PaymentVerificationResult;
import com.stayease.dto.RefundRequest;
import com.stayease.dto.RefundResult;
import com.stayease.dto.WebhookVerificationResult;
import com.stayease.exception.PaymentGatewayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * External production payment gateway adapter (Razorpay).
 * If live credentials are not set in the environment, operations safely fail fast with an
 * explicit message indicating the integration is pending external configuration.
 * Never stores raw card details and never simulates real payments when unconfigured.
 */
@Component
public class RazorpayPaymentProvider implements PaymentProvider {

    public static final String PROVIDER_NAME = "RAZORPAY";

    @Value("${app.payment.razorpay.key-id:}")
    private String keyId;

    @Value("${app.payment.razorpay.key-secret:}")
    private String keySecret;

    @Value("${app.payment.razorpay.webhook-secret:}")
    private String webhookSecret;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isConfigured() {
        return keyId != null && !keyId.trim().isEmpty() &&
               keySecret != null && !keySecret.trim().isEmpty();
    }

    @Override
    public PaymentSessionResponse initiatePayment(PaymentInitiationRequest request) {
        if (!isConfigured()) {
            throw new PaymentGatewayException(
                    "Razorpay integration is pending configuration of RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET."
            );
        }
        // When configured in production, calls Razorpay API: POST /v1/orders
        throw new UnsupportedOperationException("Production gateway calls require verified live sandbox credentials.");
    }

    @Override
    public PaymentVerificationResult verifyPayment(PaymentVerificationRequest request) {
        if (!isConfigured()) {
            throw new PaymentGatewayException(
                    "Razorpay server verification cannot proceed: missing provider credentials."
            );
        }

        if (request.gatewayOrderId() == null || request.gatewayPaymentId() == null || request.gatewaySignature() == null) {
            return PaymentVerificationResult.failure(request.transactionReference(), request.gatewayOrderId(), "Missing verification parameters.");
        }

        String data = request.gatewayOrderId() + "|" + request.gatewayPaymentId();
        String expected = calculateHmacSha256(data, keySecret);

        boolean match = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                request.gatewaySignature().getBytes(StandardCharsets.UTF_8)
        );

        if (!match) {
            return PaymentVerificationResult.failure(request.transactionReference(), request.gatewayOrderId(), "Invalid Razorpay payment signature.");
        }

        return PaymentVerificationResult.success(request.transactionReference(), request.gatewayPaymentId(), null, "GATEWAY_VERIFIED");
    }

    @Override
    public RefundResult refundPayment(RefundRequest request) {
        if (!isConfigured()) {
            throw new PaymentGatewayException(
                    "Razorpay refund cannot proceed: gateway integration is pending configuration."
            );
        }
        throw new UnsupportedOperationException("Production refund requires active provider credentials.");
    }

    @Override
    public WebhookVerificationResult verifyWebhookSignature(String rawPayload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
            return WebhookVerificationResult.failure("Razorpay webhook secret is not configured.");
        }
        if (rawPayload == null || signatureHeader == null) {
            return WebhookVerificationResult.failure("Missing payload or signature header.");
        }

        String expected = calculateHmacSha256(rawPayload, webhookSecret);
        boolean match = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().getBytes(StandardCharsets.UTF_8)
        );

        if (!match) {
            return WebhookVerificationResult.failure("Invalid Razorpay webhook signature.");
        }
        return WebhookVerificationResult.success();
    }

    @Override
    public ParsedWebhookEvent parseWebhook(String rawPayload) {
        throw new UnsupportedOperationException("External provider webhook parsing requires live payload format.");
    }

    private String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new PaymentGatewayException("Failed to compute HMAC-SHA256 signature: " + e.getMessage(), e);
        }
    }
}

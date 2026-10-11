package com.stayease.service.payment;

import com.stayease.dto.ParsedWebhookEvent;
import com.stayease.dto.PaymentInitiationRequest;
import com.stayease.dto.PaymentSessionResponse;
import com.stayease.dto.PaymentVerificationRequest;
import com.stayease.dto.PaymentVerificationResult;
import com.stayease.dto.RefundRequest;
import com.stayease.dto.RefundResult;
import com.stayease.dto.WebhookVerificationResult;

/**
 * Common abstraction for external payment providers (Razorpay, Stripe, Sandbox).
 */
public interface PaymentProvider {

    /**
     * Unique identifier of the payment provider (e.g. "SANDBOX", "RAZORPAY").
     */
    String getProviderName();

    /**
     * Indicates whether live provider credentials and secrets are configured.
     */
    boolean isConfigured();

    /**
     * Initiates a payment session or creates an order at the gateway.
     */
    PaymentSessionResponse initiatePayment(PaymentInitiationRequest request);

    /**
     * Performs server-side cryptographic or API-based verification of a completed payment.
     * Never trusts browser redirects alone.
     */
    PaymentVerificationResult verifyPayment(PaymentVerificationRequest request);

    /**
     * Executes a refund through verified provider operations.
     */
    RefundResult refundPayment(RefundRequest request);

    /**
     * Cryptographically verifies the webhook request signature.
     */
    WebhookVerificationResult verifyWebhookSignature(String rawPayload, String signatureHeader);

    /**
     * Parses the gateway webhook payload into normalized event data.
     */
    ParsedWebhookEvent parseWebhook(String rawPayload);
}

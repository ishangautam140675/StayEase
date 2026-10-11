package com.stayease.dto;

import java.util.Map;

/**
 * DTO for server-side payment verification.
 * Contains payment gateway proofs (payment ID, order ID, cryptographic signature).
 */
public record PaymentVerificationRequest(
        String transactionReference,
        String gatewayOrderId,
        String gatewayPaymentId,
        String gatewaySignature,
        Map<String, String> additionalParams
) {
    public PaymentVerificationRequest(String transactionReference, String gatewayOrderId, String gatewayPaymentId, String gatewaySignature) {
        this(transactionReference, gatewayOrderId, gatewayPaymentId, gatewaySignature, Map.of());
    }
}

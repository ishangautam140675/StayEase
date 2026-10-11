package com.stayease.dto;

import com.stayease.model.PaymentStatus;

import java.math.BigDecimal;

/**
 * Result of a server-side payment verification.
 */
public record PaymentVerificationResult(
        boolean verified,
        String transactionReference,
        String gatewayReference,
        PaymentStatus status,
        BigDecimal amount,
        String paymentMethod,
        String failureReason
) {
    public static PaymentVerificationResult success(String transactionReference, String gatewayReference, BigDecimal amount, String paymentMethod) {
        return new PaymentVerificationResult(true, transactionReference, gatewayReference, PaymentStatus.SUCCESSFUL, amount, paymentMethod, null);
    }

    public static PaymentVerificationResult failure(String transactionReference, String gatewayReference, String reason) {
        return new PaymentVerificationResult(false, transactionReference, gatewayReference, PaymentStatus.FAILED, null, null, reason);
    }
}

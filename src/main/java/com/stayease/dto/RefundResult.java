package com.stayease.dto;

import java.math.BigDecimal;

/**
 * Result of a payment refund operation.
 */
public record RefundResult(
        boolean success,
        String refundReference,
        BigDecimal refundAmount,
        String reason,
        String failureReason
) {
    public static RefundResult success(String refundReference, BigDecimal amount, String reason) {
        return new RefundResult(true, refundReference, amount, reason, null);
    }

    public static RefundResult failure(String reason, String failureReason) {
        return new RefundResult(false, null, null, reason, failureReason);
    }
}

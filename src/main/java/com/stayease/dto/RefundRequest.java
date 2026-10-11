package com.stayease.dto;

import java.math.BigDecimal;

/**
 * Request DTO for executing a refund.
 */
public record RefundRequest(
        String transactionReference,
        String gatewayPaymentReference,
        BigDecimal amount,
        String reason,
        String idempotencyKey
) {
}

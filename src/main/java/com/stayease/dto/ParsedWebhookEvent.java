package com.stayease.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Normalized webhook event data extracted from provider payload.
 */
public record ParsedWebhookEvent(
        String eventId,
        String eventType,
        String provider,
        String transactionReference,
        String gatewayPaymentId,
        String gatewayOrderId,
        BigDecimal amount,
        String currency,
        String paymentStatus,
        String paymentMethod,
        String failureReason,
        String refundReference,
        BigDecimal refundAmount,
        Map<String, Object> metadata
) {
}

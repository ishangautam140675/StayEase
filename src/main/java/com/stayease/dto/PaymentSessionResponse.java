package com.stayease.dto;

import com.stayease.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response DTO returned to clients upon initiating a payment session.
 * Exposes safe client tokens/identifiers, never secret gateway keys.
 */
public record PaymentSessionResponse(
        String transactionReference,
        String gatewayReference,
        BigDecimal amount,
        String currency,
        String provider,
        PaymentStatus status,
        String checkoutUrl,
        Map<String, Object> clientPayload
) {
}

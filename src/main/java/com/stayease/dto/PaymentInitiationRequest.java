package com.stayease.dto;

import java.math.BigDecimal;

/**
 * Request DTO for initiating a payment checkout session.
 */
public record PaymentInitiationRequest(
        Long bookingId,
        String bookingReference,
        BigDecimal amount,
        String currency,
        String userEmail,
        String userName,
        String idempotencyKey
) {
}

package com.stayease.dto;

/**
 * Result of cryptographic webhook signature validation.
 */
public record WebhookVerificationResult(
        boolean valid,
        String failureReason
) {
    public static WebhookVerificationResult success() {
        return new WebhookVerificationResult(true, null);
    }

    public static WebhookVerificationResult failure(String failureReason) {
        return new WebhookVerificationResult(false, failureReason);
    }
}

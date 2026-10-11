package com.stayease.controller;

import com.stayease.exception.InvalidSignatureException;
import com.stayease.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller receiving asynchronous payment gateway webhooks.
 * Validates cryptographic signatures, rejects invalid payloads,
 * and processes events idempotently to handle delayed, duplicate, and out-of-order events.
 */
@RestController
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping({"/api/webhooks/payment", "/webhooks/payment"})
    public ResponseEntity<Map<String, Object>> handlePaymentWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String standardSignature,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature,
            @RequestParam(name = "provider", defaultValue = "SANDBOX") String provider) {

        String signature = standardSignature != null ? standardSignature : razorpaySignature;

        if (payload == null || payload.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", "Missing webhook payload body."));
        }

        if (signature == null || signature.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", "Missing webhook signature header."));
        }

        try {
            String outcome = paymentService.processWebhook(payload, signature, provider);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "outcome", outcome
            ));
        } catch (InvalidSignatureException e) {
            log.warn("Webhook rejected due to invalid signature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Internal error handling webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Webhook processing failed."));
        }
    }
}

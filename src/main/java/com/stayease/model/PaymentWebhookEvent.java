package com.stayease.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity for tracking received payment gateway webhooks.
 * Uses a unique constraint on event_id for strict duplicate prevention and idempotency.
 */
@Entity
@Table(name = "payment_webhook_events", indexes = {
    @Index(name = "idx_webhook_event_id", columnList = "event_id", unique = true),
    @Index(name = "idx_webhook_provider", columnList = "provider"),
    @Index(name = "idx_webhook_received", columnList = "received_at")
})
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 128, nullable = false, unique = true)
    private String eventId;

    @Column(name = "provider", length = 32, nullable = false)
    private String provider;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "signature", length = 256)
    private String signature;

    @Lob
    @Column(name = "payload_masked", columnDefinition = "TEXT")
    private String payloadMasked;

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "processing_error", length = 500)
    private String processingError;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public PaymentWebhookEvent() {
    }

    public PaymentWebhookEvent(String eventId, String provider, String eventType, String signature, String payloadMasked) {
        this.eventId = eventId;
        this.provider = provider;
        this.eventType = eventType;
        this.signature = signature;
        this.payloadMasked = payloadMasked;
        this.processed = false;
    }

    @PrePersist
    protected void onCreate() {
        if (this.receivedAt == null) {
            this.receivedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getPayloadMasked() {
        return payloadMasked;
    }

    public void setPayloadMasked(String payloadMasked) {
        this.payloadMasked = payloadMasked;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public String getProcessingError() {
        return processingError;
    }

    public void setProcessingError(String processingError) {
        this.processingError = processingError;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentWebhookEvent that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

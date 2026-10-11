package com.stayease.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing an individual payment transaction linked to a {@link Booking}.
 * Stores safe transaction identifiers, provider metadata, and verification history.
 * Never stores raw card numbers, CVVs, or secret credentials.
 */
@Entity
@Table(name = "payment_transactions", indexes = {
    @Index(name = "idx_payment_booking", columnList = "booking_id"),
    @Index(name = "idx_payment_reference", columnList = "transaction_reference", unique = true),
    @Index(name = "idx_payment_gateway_ref", columnList = "gateway_reference"),
    @Index(name = "idx_payment_idempotency", columnList = "idempotency_key", unique = true)
})
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "transaction_reference", length = 64, nullable = false, unique = true)
    private String transactionReference;

    @Column(name = "gateway_reference", length = 128)
    private String gatewayReference;

    @Column(name = "provider", length = 32, nullable = false)
    private String provider;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 8, nullable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "idempotency_key", length = 128, unique = true)
    private String idempotencyKey;

    @Column(name = "payment_method", length = 32)
    private String paymentMethod;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "refund_reference", length = 128)
    private String refundReference;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public PaymentTransaction() {
    }

    public PaymentTransaction(Booking booking, String transactionReference, String provider, BigDecimal amount, String currency, String idempotencyKey) {
        this.booking = booking;
        this.transactionReference = transactionReference;
        this.provider = provider;
        this.amount = amount;
        this.currency = (currency != null && !currency.trim().isEmpty()) ? currency : "INR";
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.currency == null) {
            this.currency = "INR";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    public void setGatewayReference(String gatewayReference) {
        this.gatewayReference = gatewayReference;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getRefundReference() {
        return refundReference;
    }

    public void setRefundReference(String refundReference) {
        this.refundReference = refundReference;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentTransaction that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

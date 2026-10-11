package com.stayease.service;

import com.stayease.dto.ParsedWebhookEvent;
import com.stayease.dto.PaymentInitiationRequest;
import com.stayease.dto.PaymentSessionResponse;
import com.stayease.dto.PaymentVerificationRequest;
import com.stayease.dto.PaymentVerificationResult;
import com.stayease.dto.RefundRequest;
import com.stayease.dto.RefundResult;
import com.stayease.dto.WebhookVerificationResult;
import com.stayease.exception.BookingNotFoundException;
import com.stayease.exception.InvalidBookingStateException;
import com.stayease.exception.InvalidSignatureException;
import com.stayease.exception.PaymentGatewayException;
import com.stayease.exception.PaymentValidationException;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.PaymentStatus;
import com.stayease.model.PaymentTransaction;
import com.stayease.model.PaymentWebhookEvent;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.PaymentTransactionRepository;
import com.stayease.repository.PaymentWebhookEventRepository;
import com.stayease.service.payment.PaymentProvider;
import com.stayease.service.payment.PaymentProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service managing payment operations, server-side verifications, cryptographic webhooks,
 * idempotent transactions, and reconciliation with booking and cancellation policies.
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final BookingRepository bookingRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final PaymentProviderRegistry providerRegistry;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    public PaymentService(BookingRepository bookingRepository,
                          PaymentTransactionRepository paymentTransactionRepository,
                          PaymentWebhookEventRepository paymentWebhookEventRepository,
                          PaymentProviderRegistry providerRegistry,
                          AuditLogService auditLogService,
                          EmailService emailService) {
        this.bookingRepository = bookingRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
        this.providerRegistry = providerRegistry;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
    }

    /**
     * Initiates a payment session for a booking.
     * Enforces idempotency to prevent duplicate charges.
     */
    @Transactional
    public PaymentSessionResponse initiatePayment(Long bookingId, String userEmail, String idempotencyKey) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found with id: " + bookingId));

        if (userEmail != null && (booking.getUser() == null || !booking.getUser().getEmail().equalsIgnoreCase(userEmail))) {
            throw new AccessDeniedException("You do not have permission to pay for this booking.");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.REJECTED) {
            throw new InvalidBookingStateException("Cannot initiate payment for a " + booking.getStatus() + " booking.");
        }

        if (booking.getPaymentStatus() == PaymentStatus.SUCCESSFUL) {
            throw new IllegalStateException("Booking #" + bookingId + " has already been paid successfully.");
        }

        // Idempotency: Return existing pending transaction if same key is provided
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            Optional<PaymentTransaction> existingTxn = paymentTransactionRepository.findByIdempotencyKey(idempotencyKey.trim());
            if (existingTxn.isPresent()) {
                PaymentTransaction txn = existingTxn.get();
                if (txn.getStatus() == PaymentStatus.PENDING || txn.getStatus() == PaymentStatus.SUCCESSFUL) {
                    log.info("Returning existing payment session for idempotency key: {}", idempotencyKey);
                    return new PaymentSessionResponse(
                            txn.getTransactionReference(),
                            txn.getGatewayReference(),
                            txn.getAmount(),
                            txn.getCurrency(),
                            txn.getProvider(),
                            txn.getStatus(),
                            "/customer/bookings/" + booking.getId() + "/payment?txn=" + txn.getTransactionReference(),
                            java.util.Map.of("idempotentReplay", true)
                    );
                }
            }
        }

        BigDecimal amount = booking.getTotalPrice();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentValidationException("Cannot initiate payment: invalid booking total amount.");
        }

        PaymentProvider provider = providerRegistry.getActiveProvider();

        PaymentInitiationRequest request = new PaymentInitiationRequest(
                booking.getId(),
                booking.getBookingReference(),
                amount,
                "INR",
                booking.getUser() != null ? booking.getUser().getEmail() : null,
                booking.getUser() != null ? booking.getUser().getFullName() : null,
                idempotencyKey
        );

        PaymentSessionResponse session = provider.initiatePayment(request);

        PaymentTransaction transaction = new PaymentTransaction(
                booking,
                session.transactionReference(),
                session.provider(),
                session.amount(),
                session.currency(),
                idempotencyKey
        );
        transaction.setGatewayReference(session.gatewayReference());
        transaction.setStatus(PaymentStatus.PENDING);
        paymentTransactionRepository.save(transaction);

        booking.setPaymentStatus(PaymentStatus.PENDING);
        bookingRepository.save(booking);

        if (auditLogService != null) {
            auditLogService.log(
                    userEmail != null ? userEmail : "SYSTEM",
                    "PAYMENT_INITIATED",
                    "BOOKING",
                    String.valueOf(booking.getId()),
                    "Initiated payment session " + session.transactionReference() + " for ₹" + amount
            );
        }

        return session;
    }

    /**
     * Verifies payment status on the server using provider cryptographic checks.
     * Never trusts browser redirects or raw client parameters alone.
     */
    @Transactional
    public PaymentVerificationResult verifyAndConfirmPayment(Long bookingId, String userEmail, PaymentVerificationRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found with id: " + bookingId));

        if (userEmail != null && (booking.getUser() == null || !booking.getUser().getEmail().equalsIgnoreCase(userEmail))) {
            throw new AccessDeniedException("You do not have permission to verify payment for this booking.");
        }

        PaymentTransaction transaction = paymentTransactionRepository.findByTransactionReference(request.transactionReference())
                .orElseThrow(() -> new PaymentValidationException("Payment transaction not found for reference: " + request.transactionReference()));

        if (!transaction.getBooking().getId().equals(booking.getId())) {
            throw new PaymentValidationException("Transaction reference does not match this booking.");
        }

        // Idempotency: if already marked SUCCESSFUL, return success directly
        if (transaction.getStatus() == PaymentStatus.SUCCESSFUL) {
            return PaymentVerificationResult.success(
                    transaction.getTransactionReference(),
                    transaction.getGatewayReference(),
                    transaction.getAmount(),
                    transaction.getPaymentMethod()
            );
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidBookingStateException("Cannot confirm payment for an already cancelled booking.");
        }

        PaymentProvider provider = providerRegistry.getProvider(transaction.getProvider());
        PaymentVerificationResult result = provider.verifyPayment(request);

        if (result.verified()) {
            transaction.setStatus(PaymentStatus.SUCCESSFUL);
            transaction.setVerifiedAt(LocalDateTime.now());
            if (result.gatewayReference() != null) {
                transaction.setGatewayReference(result.gatewayReference());
            }
            if (result.paymentMethod() != null) {
                transaction.setPaymentMethod(result.paymentMethod());
            }
            paymentTransactionRepository.save(transaction);

            booking.setPaymentStatus(PaymentStatus.SUCCESSFUL);
            bookingRepository.save(booking);

            if (auditLogService != null) {
                auditLogService.log(
                        userEmail != null ? userEmail : "SYSTEM",
                        "PAYMENT_VERIFIED_SUCCESSFUL",
                        "BOOKING",
                        String.valueOf(booking.getId()),
                        "Payment verified successfully: " + transaction.getTransactionReference() + " (Gateway: " + transaction.getGatewayReference() + ")"
                );
            }

            if (emailService != null) {
                // Email confirmation can be notified
                log.info("Payment confirmed for booking #{}", booking.getId());
            }

            return result;
        } else {
            transaction.setStatus(PaymentStatus.FAILED);
            transaction.setFailureReason(result.failureReason());
            paymentTransactionRepository.save(transaction);

            booking.setPaymentStatus(PaymentStatus.FAILED);
            bookingRepository.save(booking);

            if (auditLogService != null) {
                auditLogService.log(
                        userEmail != null ? userEmail : "SYSTEM",
                        "PAYMENT_VERIFICATION_FAILED",
                        "BOOKING",
                        String.valueOf(booking.getId()),
                        "Payment verification failed for " + transaction.getTransactionReference() + ". Reason: " + result.failureReason()
                );
            }

            return result;
        }
    }

    /**
     * Executes a refund for a previously successful payment through verified provider operations.
     */
    @Transactional
    public RefundResult refundBookingPayment(Long bookingId, String reason, String initiatedBy) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getPaymentStatus() != PaymentStatus.SUCCESSFUL) {
            throw new PaymentValidationException("Booking #" + bookingId + " is not in a refundable payment status (current: " + booking.getPaymentStatus() + ")");
        }

        PaymentTransaction transaction = paymentTransactionRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(bookingId, PaymentStatus.SUCCESSFUL)
                .orElseThrow(() -> new PaymentValidationException("No successful payment transaction found to refund for booking #" + bookingId));

        PaymentProvider provider = providerRegistry.getProvider(transaction.getProvider());

        RefundRequest refundRequest = new RefundRequest(
                transaction.getTransactionReference(),
                transaction.getGatewayReference(),
                transaction.getAmount(),
                reason != null ? reason : "Requested refund",
                "rfnd_key_" + bookingId + "_" + System.currentTimeMillis()
        );

        RefundResult refundResult = provider.refundPayment(refundRequest);

        if (refundResult.success()) {
            transaction.setStatus(PaymentStatus.REFUNDED);
            transaction.setRefundReference(refundResult.refundReference());
            transaction.setRefundAmount(refundResult.refundAmount());
            transaction.setRefundReason(refundResult.reason());
            paymentTransactionRepository.save(transaction);

            booking.setPaymentStatus(PaymentStatus.REFUNDED);
            bookingRepository.save(booking);

            if (auditLogService != null) {
                auditLogService.log(
                        initiatedBy != null ? initiatedBy : "SYSTEM",
                        "PAYMENT_REFUNDED",
                        "BOOKING",
                        String.valueOf(booking.getId()),
                        "Processed refund " + refundResult.refundReference() + " for ₹" + refundResult.refundAmount() + ". Reason: " + refundResult.reason()
                );
            }

            return refundResult;
        } else {
            throw new PaymentGatewayException("Refund failed from provider: " + refundResult.failureReason());
        }
    }

    /**
     * Ingests, cryptographically verifies, and idempotently processes payment gateway webhooks.
     * Prevents duplicate, delayed, and out-of-order webhook event processing.
     */
    @Transactional
    public String processWebhook(String rawPayload, String signatureHeader, String providerName) {
        PaymentProvider provider = providerRegistry.getProvider(providerName);

        WebhookVerificationResult sigResult = provider.verifyWebhookSignature(rawPayload, signatureHeader);
        if (!sigResult.valid()) {
            log.warn("Rejected webhook with invalid signature from provider: {}", provider.getProviderName());
            throw new InvalidSignatureException("Webhook signature verification failed: " + sigResult.failureReason());
        }

        ParsedWebhookEvent event = provider.parseWebhook(rawPayload);

        // 1. Idempotency Check: prevent duplicate webhook execution
        if (paymentWebhookEventRepository.existsByEventId(event.eventId())) {
            log.info("Duplicate webhook event ignored (idempotent): {}", event.eventId());
            return "ALREADY_PROCESSED";
        }

        PaymentWebhookEvent webhookEvent = new PaymentWebhookEvent(
                event.eventId(),
                provider.getProviderName(),
                event.eventType(),
                signatureHeader,
                rawPayload != null && rawPayload.length() > 2000 ? rawPayload.substring(0, 2000) : rawPayload
        );

        try {
            // 2. Reconcile Event with Local State
            reconcileWebhookEvent(event);
            webhookEvent.setProcessed(true);
            webhookEvent.setProcessedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Failed to reconcile webhook event {}: {}", event.eventId(), e.getMessage(), e);
            webhookEvent.setProcessed(false);
            webhookEvent.setProcessingError(e.getMessage());
        }

        paymentWebhookEventRepository.save(webhookEvent);
        return webhookEvent.isProcessed() ? "PROCESSED" : "ERROR";
    }

    private void reconcileWebhookEvent(ParsedWebhookEvent event) {
        Optional<PaymentTransaction> optTxn = Optional.empty();
        if (event.transactionReference() != null) {
            optTxn = paymentTransactionRepository.findByTransactionReference(event.transactionReference());
        }
        if (optTxn.isEmpty() && event.gatewayOrderId() != null) {
            optTxn = paymentTransactionRepository.findByGatewayReference(event.gatewayOrderId());
        }

        if (optTxn.isEmpty()) {
            log.warn("Webhook event {} does not match any local transaction reference", event.eventId());
            return;
        }

        PaymentTransaction txn = optTxn.get();
        Booking booking = txn.getBooking();

        if ("payment.captured".equalsIgnoreCase(event.eventType()) || "payment.successful".equalsIgnoreCase(event.eventType())) {
            // Out-of-order check: If booking is already cancelled, do NOT reopen or approve
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                log.warn("Late payment.captured received for already cancelled booking #{}. Flagging for refund.", booking.getId());
                txn.setStatus(PaymentStatus.SUCCESSFUL);
                txn.setVerifiedAt(LocalDateTime.now());
                paymentTransactionRepository.save(txn);

                booking.setPaymentStatus(PaymentStatus.SUCCESSFUL);
                bookingRepository.save(booking);

                // Trigger safe automatic refund
                refundBookingPayment(booking.getId(), "Late payment webhook received after booking cancellation", "SYSTEM_WEBHOOK");
                return;
            }

            if (txn.getStatus() != PaymentStatus.SUCCESSFUL) {
                txn.setStatus(PaymentStatus.SUCCESSFUL);
                txn.setVerifiedAt(LocalDateTime.now());
                if (event.gatewayPaymentId() != null) {
                    txn.setGatewayReference(event.gatewayPaymentId());
                }
                paymentTransactionRepository.save(txn);

                booking.setPaymentStatus(PaymentStatus.SUCCESSFUL);
                bookingRepository.save(booking);

                if (auditLogService != null) {
                    auditLogService.log("WEBHOOK", "PAYMENT_CAPTURED", "BOOKING", String.valueOf(booking.getId()),
                            "Webhook " + event.eventId() + " confirmed payment for " + txn.getTransactionReference());
                }
            }
        } else if ("payment.failed".equalsIgnoreCase(event.eventType())) {
            if (txn.getStatus() != PaymentStatus.SUCCESSFUL) {
                txn.setStatus(PaymentStatus.FAILED);
                txn.setFailureReason(event.failureReason());
                paymentTransactionRepository.save(txn);

                booking.setPaymentStatus(PaymentStatus.FAILED);
                bookingRepository.save(booking);

                if (auditLogService != null) {
                    auditLogService.log("WEBHOOK", "PAYMENT_FAILED", "BOOKING", String.valueOf(booking.getId()),
                            "Webhook " + event.eventId() + " reported payment failure for " + txn.getTransactionReference());
                }
            }
        } else if ("refund.processed".equalsIgnoreCase(event.eventType())) {
            txn.setStatus(PaymentStatus.REFUNDED);
            txn.setRefundReference(event.refundReference());
            paymentTransactionRepository.save(txn);

            booking.setPaymentStatus(PaymentStatus.REFUNDED);
            bookingRepository.save(booking);

            if (auditLogService != null) {
                auditLogService.log("WEBHOOK", "REFUND_PROCESSED", "BOOKING", String.valueOf(booking.getId()),
                        "Webhook " + event.eventId() + " confirmed refund for " + txn.getTransactionReference());
            }
        }
    }

    /**
     * Lists all payment transactions for a given booking.
     */
    public List<PaymentTransaction> getTransactionsForBooking(Long bookingId) {
        return paymentTransactionRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
    }
}

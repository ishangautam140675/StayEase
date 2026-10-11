package com.stayease.service;

import com.stayease.dto.PaymentSessionResponse;
import com.stayease.dto.PaymentVerificationRequest;
import com.stayease.dto.PaymentVerificationResult;
import com.stayease.dto.RefundResult;
import com.stayease.exception.InvalidSignatureException;
import com.stayease.exception.PaymentGatewayException;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.PaymentStatus;
import com.stayease.model.PaymentTransaction;
import com.stayease.model.PaymentWebhookEvent;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.PaymentTransactionRepository;
import com.stayease.repository.PaymentWebhookEventRepository;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import com.stayease.service.payment.RazorpayPaymentProvider;
import com.stayease.service.payment.SandboxPaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("default")
@Transactional
public class Phase6PaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SandboxPaymentProvider sandboxProvider;

    @Autowired
    private RazorpayPaymentProvider razorpayProvider;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    private User testCustomer;
    private Room testRoom;
    private Booking testBooking;

    @BeforeEach
    void setUp() {
        testCustomer = userRepository.findByEmail("phase6_pay_guest@example.com")
                .orElseGet(() -> userRepository.save(new User("Phase6 Guest", "phase6_pay_guest@example.com", "Secret@123", "9876543210", Role.CUSTOMER)));

        testRoom = roomRepository.findByRoomNumberIgnoreCase("P6-101")
                .orElseGet(() -> roomRepository.save(new Room("P6-101", RoomType.SINGLE, 1, BigDecimal.valueOf(1500.00), "Phase 6 Room", true)));

        testBooking = new Booking(
                testCustomer,
                testRoom,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 4),
                1,
                BookingStatus.PENDING,
                null
        );
        testBooking.setPricePerNightAtBooking(BigDecimal.valueOf(1500.00));
        testBooking.setTotalPrice(BigDecimal.valueOf(4500.00));
        testBooking.setPaymentStatus(PaymentStatus.UNPAID);
        testBooking = bookingRepository.save(testBooking);
    }

    // ── 1. Separate Booking Status & Payment Initiation ─────────────────────

    @Test
    @DisplayName("Initiating payment creates pending transaction while preserving pending booking status")
    void initiatePaymentSeparatesStatuses() {
        String idempotencyKey = "IDEM_INIT_001";
        PaymentSessionResponse session = paymentService.initiatePayment(
                testBooking.getId(),
                testCustomer.getEmail(),
                idempotencyKey
        );

        assertThat(session.transactionReference()).startsWith("TXN-");
        assertThat(session.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(session.amount()).isEqualByComparingTo(BigDecimal.valueOf(4500.00));

        Booking updated = bookingRepository.findById(testBooking.getId()).orElseThrow();
        // Booking status remains PENDING (separated from payment)
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(updated.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    // ── 2. Idempotency Controls (Repeated Requests) ────────────────────────

    @Test
    @DisplayName("Repeated payment requests with the same idempotency key return the same session without duplicate transaction")
    void repeatedPaymentRequestIsIdempotent() {
        String key = "IDEM_KEY_RETRY_123";

        PaymentSessionResponse session1 = paymentService.initiatePayment(testBooking.getId(), testCustomer.getEmail(), key);
        PaymentSessionResponse session2 = paymentService.initiatePayment(testBooking.getId(), testCustomer.getEmail(), key);

        assertThat(session1.transactionReference()).isEqualTo(session2.transactionReference());
        assertThat(session1.gatewayReference()).isEqualTo(session2.gatewayReference());

        // Verify only one transaction was saved for this idempotency key
        Optional<PaymentTransaction> txn = transactionRepository.findByIdempotencyKey(key);
        assertThat(txn).isPresent();
    }

    // ── 3. Server-Side Verification (Tampering Resistance) ──────────────────

    @Test
    @DisplayName("Server verification succeeds with valid cryptographic signature and updates payment status")
    void verifyPaymentSucceedsWithValidSignature() {
        PaymentSessionResponse session = paymentService.initiatePayment(
                testBooking.getId(), testCustomer.getEmail(), "IDEM_VERIFY_VALID");

        String orderId = session.gatewayReference();
        String paymentId = "pay_sb_test_999";
        String validSignature = SandboxPaymentProvider.calculateHmacSha256(
                orderId + "|" + paymentId, sandboxProvider.getApiSecret());

        PaymentVerificationRequest req = new PaymentVerificationRequest(
                session.transactionReference(),
                orderId,
                paymentId,
                validSignature
        );

        PaymentVerificationResult result = paymentService.verifyAndConfirmPayment(
                testBooking.getId(), testCustomer.getEmail(), req);

        assertThat(result.verified()).isTrue();
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESSFUL);

        Booking updated = bookingRepository.findById(testBooking.getId()).orElseThrow();
        assertThat(updated.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);
        assertThat(updated.isPaid()).isTrue();
    }

    @Test
    @DisplayName("Server verification rejects tampered or invalid cryptographic signatures")
    void verifyPaymentRejectsTamperedSignature() {
        PaymentSessionResponse session = paymentService.initiatePayment(
                testBooking.getId(), testCustomer.getEmail(), "IDEM_VERIFY_TAMPER");

        String orderId = session.gatewayReference();
        String paymentId = "pay_sb_test_888";
        String invalidSignature = "tampered_fake_signature_abc123";

        PaymentVerificationRequest req = new PaymentVerificationRequest(
                session.transactionReference(),
                orderId,
                paymentId,
                invalidSignature
        );

        PaymentVerificationResult result = paymentService.verifyAndConfirmPayment(
                testBooking.getId(), testCustomer.getEmail(), req);

        assertThat(result.verified()).isFalse();
        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.failureReason()).contains("Cryptographic payment signature mismatch");

        Booking updated = bookingRepository.findById(testBooking.getId()).orElseThrow();
        assertThat(updated.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    // ── 4. Payment Failures & Retries ───────────────────────────────────────

    @Test
    @DisplayName("Simulated payment authorization failure transitions status to FAILED without corrupting booking")
    void paymentAuthorizationFailureHandledGracefully() {
        PaymentSessionResponse session = paymentService.initiatePayment(
                testBooking.getId(), testCustomer.getEmail(), "IDEM_PAY_FAIL");

        String orderId = session.gatewayReference();
        String paymentId = "pay_fail_card_declined";
        String validSignature = SandboxPaymentProvider.calculateHmacSha256(
                orderId + "|" + paymentId, sandboxProvider.getApiSecret());

        PaymentVerificationRequest req = new PaymentVerificationRequest(
                session.transactionReference(),
                orderId,
                paymentId,
                validSignature
        );

        PaymentVerificationResult result = paymentService.verifyAndConfirmPayment(
                testBooking.getId(), testCustomer.getEmail(), req);

        assertThat(result.verified()).isFalse();
        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);

        Booking updated = bookingRepository.findById(testBooking.getId()).orElseThrow();
        assertThat(updated.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    // ── 5. Webhook Signature Verification ──────────────────────────────────

    @Test
    @DisplayName("Webhook listener rejects invalid signatures with HTTP 400")
    void webhookRejectsInvalidSignature() throws Exception {
        String payload = "{\"event\":\"payment.captured\",\"id\":\"evt_invalid_001\"}";

        mockMvc.perform(post("/api/webhooks/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "wrong_signature")
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("Webhook listener accepts cryptographically valid signature and processes event")
    void webhookAcceptsValidSignature() throws Exception {
        PaymentSessionResponse session = paymentService.initiatePayment(
                testBooking.getId(), testCustomer.getEmail(), "IDEM_WH_OK");

        String payload = String.format(
                "{\"event\":\"payment.captured\",\"id\":\"evt_valid_100\",\"transaction_reference\":\"%s\",\"payment_id\":\"pay_sb_hook_1\"}",
                session.transactionReference()
        );

        String validSignature = SandboxPaymentProvider.calculateHmacSha256(payload, sandboxProvider.getWebhookSecret());

        mockMvc.perform(post("/api/webhooks/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", validSignature)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.outcome").value("PROCESSED"));

        Booking updated = bookingRepository.findById(testBooking.getId()).orElseThrow();
        assertThat(updated.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);
    }

    // ── 6. Duplicate Webhook Idempotency ────────────────────────────────────

    @Test
    @DisplayName("Duplicate webhook events are acknowledged idempotently without double processing")
    void duplicateWebhookIgnoredIdempotently() throws Exception {
        PaymentSessionResponse session = paymentService.initiatePayment(
                testBooking.getId(), testCustomer.getEmail(), "IDEM_WH_DUP");

        String eventId = "evt_dup_test_555";
        String payload = String.format(
                "{\"event\":\"payment.captured\",\"id\":\"%s\",\"transaction_reference\":\"%s\",\"payment_id\":\"pay_sb_hook_dup\"}",
                eventId,
                session.transactionReference()
        );

        String validSignature = SandboxPaymentProvider.calculateHmacSha256(payload, sandboxProvider.getWebhookSecret());

        // First delivery: PROCESSED
        mockMvc.perform(post("/api/webhooks/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", validSignature)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PROCESSED"));

        // Second duplicate delivery: ALREADY_PROCESSED
        mockMvc.perform(post("/api/webhooks/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", validSignature)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ALREADY_PROCESSED"));

        // Verify only 1 record exists in webhook events table
        assertThat(webhookEventRepository.findByEventId(eventId)).isPresent();
    }

    // ── 7. Reconciling Cancellation & Verified Refunds ──────────────────────

    @Test
    @DisplayName("Cancelling a paid booking within the policy window triggers an automatic verified refund")
    void cancellingPaidBookingTriggersVerifiedRefund() {
        // 1. Pay the booking
        PaymentSessionResponse session = paymentService.initiatePayment(
                testBooking.getId(), testCustomer.getEmail(), "IDEM_REFUND_FLOW");

        String orderId = session.gatewayReference();
        String paymentId = "pay_sb_refund_target";
        String sig = SandboxPaymentProvider.calculateHmacSha256(orderId + "|" + paymentId, sandboxProvider.getApiSecret());
        paymentService.verifyAndConfirmPayment(testBooking.getId(), testCustomer.getEmail(),
                new PaymentVerificationRequest(session.transactionReference(), orderId, paymentId, sig));

        Booking paidBooking = bookingRepository.findById(testBooking.getId()).orElseThrow();
        assertThat(paidBooking.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);

        // 2. Customer cancels within policy window
        bookingService.cancel(testBooking.getId(), testCustomer.getEmail());

        // 3. Status reconciled: Booking CANCELLED, Payment REFUNDED
        Booking cancelledBooking = bookingRepository.findById(testBooking.getId()).orElseThrow();
        assertThat(cancelledBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelledBooking.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    // ── 8. Inconsistent State / Out-of-Order Webhooks ───────────────────────

    @Test
    @DisplayName("Payment captured webhook received after booking cancellation is reconciled safely without reopening booking")
    void latePaymentWebhookOnCancelledBookingDoesNotReopenBooking() {
        PaymentSessionResponse session = paymentService.initiatePayment(
                testBooking.getId(), testCustomer.getEmail(), "IDEM_LATE_WH");

        // Booking is cancelled prior to webhook arrival
        testBooking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(testBooking);

        String payload = String.format(
                "{\"event\":\"payment.captured\",\"id\":\"evt_late_arrival_99\",\"transaction_reference\":\"%s\",\"payment_id\":\"pay_late\"}",
                session.transactionReference()
        );
        String sig = SandboxPaymentProvider.calculateHmacSha256(payload, sandboxProvider.getWebhookSecret());

        String outcome = paymentService.processWebhook(payload, sig, "SANDBOX");
        assertThat(outcome).isEqualTo("PROCESSED");

        Booking afterHook = bookingRepository.findById(testBooking.getId()).orElseThrow();
        // Booking remains CANCELLED; never improperly reopened
        assertThat(afterHook.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        // Payment is immediately refunded
        assertThat(afterHook.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    // ── 9. Unconfigured External Gateway Handling ──────────────────────────

    @Test
    @DisplayName("External gateway throws PaymentGatewayException when credentials are not configured")
    void unconfiguredExternalGatewayFailsExplicitly() {
        assertThat(razorpayProvider.isConfigured()).isFalse();

        assertThatThrownBy(() -> razorpayProvider.initiatePayment(null))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("pending configuration");
    }
}

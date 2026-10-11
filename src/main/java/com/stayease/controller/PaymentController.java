package com.stayease.controller;

import com.stayease.dto.PaymentSessionResponse;
import com.stayease.dto.PaymentVerificationRequest;
import com.stayease.dto.PaymentVerificationResult;
import com.stayease.dto.RefundResult;
import com.stayease.model.Booking;
import com.stayease.service.BookingService;
import com.stayease.service.PaymentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.UUID;

/**
 * Controller handling customer payment checkout, server-side callback verification,
 * and administrative refund requests.
 */
@Controller
public class PaymentController {

    private final PaymentService paymentService;
    private final BookingService bookingService;

    public PaymentController(PaymentService paymentService, BookingService bookingService) {
        this.paymentService = paymentService;
        this.bookingService = bookingService;
    }

    /**
     * Shows payment checkout screen for a booking.
     */
    @GetMapping("/customer/bookings/{id}/payment")
    public String showPaymentPage(@PathVariable("id") Long bookingId,
                                  @RequestParam(value = "txn", required = false) String transactionRef,
                                  Principal principal,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.getBookingWithDetails(bookingId, principal.getName());

        if (booking.isPaid()) {
            redirectAttributes.addFlashAttribute("successMessage", "This booking is already paid.");
            return "redirect:/customer/dashboard";
        }

        // If no active transaction initiated, initiate one with an idempotency key
        PaymentSessionResponse session;
        try {
            String idempotencyKey = "PAY_INIT_" + booking.getId() + "_" + booking.getBookingReference();
            session = paymentService.initiatePayment(bookingId, principal.getName(), idempotencyKey);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to initiate payment: " + e.getMessage());
            return "redirect:/customer/dashboard";
        }

        model.addAttribute("booking", booking);
        model.addAttribute("paymentSession", session);
        model.addAttribute("transactions", paymentService.getTransactionsForBooking(bookingId));
        return "customer/payment";
    }

    /**
     * Server-side payment verification callback endpoint.
     * Never trusts browser redirect parameters as proof of payment without cryptographic verification.
     */
    @PostMapping("/customer/bookings/{id}/payment/verify")
    public String verifyPaymentCallback(
            @PathVariable("id") Long bookingId,
            @RequestParam("transactionReference") String transactionReference,
            @RequestParam("gatewayOrderId") String gatewayOrderId,
            @RequestParam("gatewayPaymentId") String gatewayPaymentId,
            @RequestParam("gatewaySignature") String gatewaySignature,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        PaymentVerificationRequest request = new PaymentVerificationRequest(
                transactionReference,
                gatewayOrderId,
                gatewayPaymentId,
                gatewaySignature
        );

        try {
            PaymentVerificationResult result = paymentService.verifyAndConfirmPayment(bookingId, principal.getName(), request);
            if (result.verified()) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Payment verified successfully! Transaction Ref: " + result.transactionReference());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Payment verification failed: " + result.failureReason());
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Payment verification could not be completed: " + e.getMessage());
        }

        return "redirect:/customer/dashboard";
    }

    /**
     * Administrative refund operation.
     */
    @PostMapping("/admin/bookings/{id}/refund")
    public String adminRefundBooking(
            @PathVariable("id") Long bookingId,
            @RequestParam(value = "reason", defaultValue = "Administrator approved refund") String reason,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            RefundResult result = paymentService.refundBookingPayment(bookingId, reason, principal.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Refund processed successfully! Refund Ref: " + result.refundReference() + " (₹" + result.refundAmount() + ")");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to process refund: " + e.getMessage());
        }

        return "redirect:/admin/bookings";
    }
}

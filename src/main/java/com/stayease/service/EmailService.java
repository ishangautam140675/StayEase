package com.stayease.service;

import com.stayease.model.Booking;
import com.stayease.model.NotificationLog;
import com.stayease.model.NotificationStatus;
import com.stayease.model.NotificationType;
import com.stayease.model.User;
import com.stayease.repository.NotificationLogRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service managing transactional emails and outbox notification logs.
 * Features branded templates, safe retries, duplicate-delivery protection,
 * and graceful fallback when SMTP is unavailable without corrupting business transactions.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final NotificationLogRepository notificationLogRepository;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:noreply@stayease.com}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public EmailService(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    /**
     * Sends booking submission notification (PENDING status).
     */
    public void sendBookingCreated(Booking booking) {
        if (booking == null || booking.getUser() == null) return;
        String key = "BOOKING_CREATED:" + booking.getId() + ":" + booking.getUser().getEmail();
        String subject = "Reservation Received: " + booking.getBookingReference();
        String html = buildBrandedEmail(
                "Reservation Received",
                "Hello " + sanitize(booking.getUser().getFullName()) + ",",
                "We have received your reservation request for Room <strong>" + sanitize(booking.getRoom().getRoomNumber()) + "</strong>.",
                "Your booking reference is <strong>" + booking.getBookingReference() + "</strong>. Your request is currently <strong>PENDING</strong> administrator review.",
                "Check-in: <strong>" + booking.getCheckIn().format(DATE_FMT) + "</strong><br>" +
                "Check-out: <strong>" + booking.getCheckOut().format(DATE_FMT) + "</strong><br>" +
                "Total Amount: <strong>₹" + booking.getTotalPrice() + "</strong>",
                baseUrl + "/customer/bookings/" + booking.getId() + "/receipt",
                "View Booking Details & Receipt"
        );
        sendSafe(booking.getUser().getEmail(), subject, html, NotificationType.BOOKING_REQUESTED, booking.getBookingReference(), key);
    }

    /**
     * Sends booking approval confirmation.
     */
    public void sendBookingApproved(Booking booking) {
        if (booking == null || booking.getUser() == null) return;
        String key = "BOOKING_APPROVED:" + booking.getId() + ":" + booking.getUser().getEmail();
        String subject = "Reservation Approved! Reference: " + booking.getBookingReference();
        String html = buildBrandedEmail(
                "Reservation Approved",
                "Great news, " + sanitize(booking.getUser().getFullName()) + "!",
                "Your reservation for Room <strong>" + sanitize(booking.getRoom().getRoomNumber()) + "</strong> has been officially <strong>APPROVED</strong>.",
                "We look forward to welcoming you to StayEase. You can print or download your official receipt anytime.",
                "Booking Reference: <strong>" + booking.getBookingReference() + "</strong><br>" +
                "Arrival Date: <strong>" + booking.getCheckIn().format(DATE_FMT) + "</strong> (from 2:00 PM)<br>" +
                "Departure Date: <strong>" + booking.getCheckOut().format(DATE_FMT) + "</strong> (by 11:00 AM)<br>" +
                "Total Paid / Payable: <strong>₹" + booking.getTotalPrice() + "</strong>",
                baseUrl + "/customer/bookings/" + booking.getId() + "/receipt",
                "Download Official Receipt"
        );
        sendSafe(booking.getUser().getEmail(), subject, html, NotificationType.BOOKING_APPROVED, booking.getBookingReference(), key);
    }

    /**
     * Sends booking rejection notification with administrator remark.
     */
    public void sendBookingRejected(Booking booking, String adminRemark) {
        if (booking == null || booking.getUser() == null) return;
        String key = "BOOKING_REJECTED:" + booking.getId() + ":" + booking.getUser().getEmail();
        String subject = "Update on Reservation: " + booking.getBookingReference();
        String reasonHtml = adminRemark != null && !adminRemark.trim().isEmpty()
                ? "Reason provided by hostel administration: <em>" + sanitize(adminRemark.trim()) + "</em>"
                : "The requested accommodation could not be accommodated for these dates.";
        String html = buildBrandedEmail(
                "Reservation Update",
                "Dear " + sanitize(booking.getUser().getFullName()) + ",",
                "We regret to inform you that your reservation request for Room <strong>" + sanitize(booking.getRoom().getRoomNumber()) + "</strong> was not approved.",
                reasonHtml,
                "Booking Reference: <strong>" + booking.getBookingReference() + "</strong><br>" +
                "Stay Dates: <strong>" + booking.getCheckIn().format(DATE_FMT) + " - " + booking.getCheckOut().format(DATE_FMT) + "</strong>",
                baseUrl + "/rooms",
                "Browse Alternative Rooms"
        );
        sendSafe(booking.getUser().getEmail(), subject, html, NotificationType.BOOKING_REJECTED, booking.getBookingReference(), key);
    }

    /**
     * Sends booking cancellation receipt notice.
     */
    public void sendBookingCancelled(Booking booking) {
        if (booking == null || booking.getUser() == null) return;
        String key = "BOOKING_CANCELLED:" + booking.getId() + ":" + booking.getUser().getEmail();
        String subject = "Reservation Cancelled: " + booking.getBookingReference();
        String html = buildBrandedEmail(
                "Reservation Cancelled",
                "Hello " + sanitize(booking.getUser().getFullName()) + ",",
                "Your reservation for Room <strong>" + sanitize(booking.getRoom().getRoomNumber()) + "</strong> has been cancelled.",
                "This cancellation has been recorded in your account. You will not be charged any remaining balance.",
                "Booking Reference: <strong>" + booking.getBookingReference() + "</strong><br>" +
                "Dates Cancelled: <strong>" + booking.getCheckIn().format(DATE_FMT) + " - " + booking.getCheckOut().format(DATE_FMT) + "</strong>",
                baseUrl + "/customer/bookings",
                "View My Bookings"
        );
        sendSafe(booking.getUser().getEmail(), subject, html, NotificationType.BOOKING_CANCELLED, booking.getBookingReference(), key);
    }

    /**
     * Sends upcoming stay reminder.
     */
    public void sendBookingReminder(Booking booking) {
        if (booking == null || booking.getUser() == null) return;
        String key = "BOOKING_REMINDER:" + booking.getId() + ":" + booking.getUser().getEmail();
        String subject = "Upcoming Stay Reminder: " + booking.getBookingReference();
        String html = buildBrandedEmail(
                "Upcoming Stay Reminder",
                "Hello " + sanitize(booking.getUser().getFullName()) + ",",
                "Your stay at StayEase is approaching on <strong>" + booking.getCheckIn().format(DATE_FMT) + "</strong>.",
                "Check-in begins at 2:00 PM. Please have your booking reference ready upon arrival.",
                "Room: <strong>" + sanitize(booking.getRoom().getRoomNumber()) + "</strong> (" + booking.getRoom().getType() + ")<br>" +
                "Booking Reference: <strong>" + booking.getBookingReference() + "</strong>",
                baseUrl + "/customer/bookings/" + booking.getId() + "/receipt",
                "View Check-In Receipt"
        );
        sendSafe(booking.getUser().getEmail(), subject, html, NotificationType.BOOKING_REMINDER, booking.getBookingReference(), key);
    }

    /**
     * Sends single-use password reset email.
     */
    public void sendPasswordResetEmail(User user, String resetToken) {
        if (user == null || resetToken == null) return;
        String resetUrl = baseUrl + "/reset-password?token=" + resetToken;
        String subject = "StayEase — Reset Your Password";
        String html = buildBrandedEmail(
                "Password Reset Request",
                "Hello " + sanitize(user.getFullName()) + ",",
                "We received a request to reset the password for your StayEase account.",
                "This password reset link is valid for <strong>30 minutes</strong> and can only be used once. If you did not make this request, you can safely ignore this email; your account remains secure.",
                "Security Notice: Never share this link or your credentials with anyone.",
                resetUrl,
                "Reset My Password"
        );
        sendSafe(user.getEmail(), subject, html, NotificationType.PASSWORD_RESET, user.getId().toString(), null);
    }

    /**
     * Sends email address verification email.
     */
    public void sendEmailVerification(User user, String verifyToken) {
        if (user == null || verifyToken == null) return;
        String verifyUrl = baseUrl + "/verify-email?token=" + verifyToken;
        String subject = "StayEase — Verify Your Email Address";
        String html = buildBrandedEmail(
                "Verify Your Email",
                "Welcome to StayEase, " + sanitize(user.getFullName()) + "!",
                "Please verify your email address to complete your profile and enable notifications.",
                "This verification link is valid for 24 hours.",
                "Click the button below to confirm your email address.",
                verifyUrl,
                "Verify Email Address"
        );
        sendSafe(user.getEmail(), subject, html, NotificationType.EMAIL_VERIFICATION, user.getId().toString(), null);
    }

    /**
     * Safe delivery routine: prevents duplicate deliveries, records outbox audit log,
     * and catches SMTP failures without breaking business transactions.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSafe(String recipient, String subject, String htmlBody, NotificationType type, String refId, String idempotencyKey) {
        if (idempotencyKey != null && notificationLogRepository.existsByIdempotencyKeyAndStatus(idempotencyKey, NotificationStatus.SENT)) {
            log.info("Notification with key {} already sent. Skipping duplicate delivery.", idempotencyKey);
            return;
        }

        NotificationLog auditLog = idempotencyKey != null
                ? notificationLogRepository.findByIdempotencyKey(idempotencyKey).orElse(new NotificationLog(recipient, subject, type, refId, idempotencyKey))
                : new NotificationLog(recipient, subject, type, refId, idempotencyKey);

        auditLog.setAttempts(auditLog.getAttempts() + 1);
        auditLog.setLastAttemptAt(LocalDateTime.now());

        if (!mailEnabled || mailSender == null) {
            log.info("Email notifications are disabled or mailSender not configured. Notification to {} logged as SENT (simulation mode).", recipient);
            auditLog.setStatus(NotificationStatus.SENT);
            notificationLogRepository.save(auditLog);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            auditLog.setStatus(NotificationStatus.SENT);
            auditLog.setErrorMessage(null);
            log.info("Successfully sent {} email to {}", type, recipient);
        } catch (Exception e) {
            auditLog.setStatus(NotificationStatus.FAILED);
            auditLog.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 950)) : "Unknown mail error");
            log.warn("Failed to dispatch {} email to {}: {}. Transaction preserved.", type, recipient, e.getMessage());
        }

        notificationLogRepository.save(auditLog);
    }

    /**
     * Builds consistent, responsive, editorial HTML email template.
     */
    private String buildBrandedEmail(String headerTitle, String greeting, String leadText, String bodyText, String extraDetails, String actionUrl, String actionLabel) {
        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f7f6f4; margin: 0; padding: 20px; color: #1f2937; }
            .card { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; border: 1px solid #e5e7eb; }
            .header { background: #1a2b88; padding: 28px 32px; color: #ffffff; }
            .header h1 { margin: 0 0 6px 0; font-size: 20px; font-weight: 700; letter-spacing: -0.01em; }
            .content { padding: 32px; font-size: 15px; line-height: 1.6; }
            .lead { font-size: 16px; font-weight: 600; color: #111827; margin-bottom: 12px; }
            .details-box { background: #fbfaf8; border: 1px solid #f0eee9; border-radius: 8px; padding: 16px; margin: 20px 0; font-size: 14px; }
            .button-wrap { text-align: center; margin: 28px 0; }
            .btn { background-color: #1a2b88; color: #ffffff !important; padding: 12px 28px; border-radius: 50px; text-decoration: none; font-weight: 600; font-size: 14px; display: inline-block; }
            .footer { background: #faf9f6; padding: 20px 32px; font-size: 12px; color: #6b7280; text-align: center; border-top: 1px solid #f3f4f6; }
          </style>
        </head>
        <body>
          <div class="card">
            <div class="header">
              <div style="font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; color: #ff7a30; margin-bottom: 4px;">STAYEASE HOSPITALITY</div>
              <h1>""" + headerTitle + """
              </h1>
            </div>
            <div class="content">
              <div class="lead">""" + greeting + """
              </div>
              <p>""" + leadText + """
              </p>
              <p>""" + bodyText + """
              </p>
              """ + (extraDetails != null && !extraDetails.isEmpty() ? "<div class=\"details-box\">" + extraDetails + "</div>" : "") + """
              """ + (actionUrl != null && !actionUrl.isEmpty() ? "<div class=\"button-wrap\"><a href=\"" + actionUrl + "\" class=\"btn\">" + actionLabel + "</a></div>" : "") + """
            </div>
            <div class="footer">
              StayEase Hospitality &bull; Safe, Verified, Community-First Stays<br>
              Need assistance? Contact support@stayease.com
            </div>
          </div>
        </body>
        </html>
        """;
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public boolean isMailEnabled() {
        return mailEnabled;
    }

    public void setMailEnabled(boolean mailEnabled) {
        this.mailEnabled = mailEnabled;
    }

    public void setMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
}

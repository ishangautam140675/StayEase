package com.stayease.service;

import com.stayease.model.Booking;
import com.stayease.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Phase 9: Scheduled reminder service for upcoming guest check-ins.
 * Dispatches transactional emails and in-app notifications to guests whose
 * reservation starts the next day.
 */
@Service
public class BookingReminderService {

    private static final Logger log = LoggerFactory.getLogger(BookingReminderService.class);

    private final BookingRepository bookingRepository;
    private final EmailService emailService;

    @Autowired(required = false)
    private InAppNotificationService notificationService;

    public BookingReminderService(BookingRepository bookingRepository,
                                  EmailService emailService) {
        this.bookingRepository = bookingRepository;
        this.emailService = emailService;
    }

    public void setNotificationService(InAppNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Daily scheduled job running at 9:00 AM (server time).
     * Dispatches reminders for stays commencing tomorrow.
     */
    @Scheduled(cron = "${app.scheduling.reminder-cron:0 0 9 * * *}")
    public void runScheduledCheckInReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        log.info("Running scheduled check-in reminders for date: {}", tomorrow);
        int sent = sendUpcomingCheckInReminders(tomorrow);
        log.info("Completed scheduled check-in reminders. Total sent: {}", sent);
    }

    /**
     * Dispatches reminders for all approved bookings checking in on the specified date.
     *
     * @param targetDate the check-in date
     * @return count of reminders processed
     */
    @Transactional(readOnly = true)
    public int sendUpcomingCheckInReminders(LocalDate targetDate) {
        if (targetDate == null) {
            return 0;
        }

        List<Booking> upcomingBookings = bookingRepository.findApprovedBookingsByCheckInDate(targetDate);
        if (upcomingBookings == null || upcomingBookings.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Booking booking : upcomingBookings) {
            if (booking.getUser() == null) continue;

            // Send transactional email
            if (emailService != null) {
                try {
                    emailService.sendBookingReminder(booking);
                } catch (Exception e) {
                    log.warn("Failed to dispatch email reminder for booking #{}: {}", booking.getId(), e.getMessage());
                }
            }

            // Send real-time in-app notification
            if (notificationService != null) {
                try {
                    String roomNum = booking.getRoom() != null ? booking.getRoom().getRoomNumber() : "";
                    notificationService.notifyUser(
                            booking.getUser().getEmail(),
                            "Upcoming Stay Tomorrow!",
                            "Reminder: Your stay in Room " + roomNum + " (" + booking.getBookingReference() + ") starts tomorrow. Check-in starts at 2:00 PM.",
                            "BOOKING_REMINDER",
                            "/customer/bookings/" + booking.getId() + "/receipt"
                    );
                } catch (Exception e) {
                    log.warn("Failed to dispatch in-app reminder for booking #{}: {}", booking.getId(), e.getMessage());
                }
            }
            count++;
        }
        return count;
    }
}

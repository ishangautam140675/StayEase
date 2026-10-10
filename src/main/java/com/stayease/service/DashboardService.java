package com.stayease.service;

import com.stayease.dto.AdminDashboardStats;
import com.stayease.dto.CustomerDashboardView;
import com.stayease.dto.MonthBookingCount;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Service providing specialized statistics and overview data for admin and customer dashboards (Phase 13).
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public DashboardService(RoomRepository roomRepository, BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Aggregates live system-wide operational metrics for the administrator console.
     */
    public AdminDashboardStats adminStats() {
        long totalRooms = roomRepository.count();
        long activeRooms = roomRepository.countByActiveTrue();
        LocalDate today = LocalDate.now();

        long occupiedToday = bookingRepository.countOccupiedRoomsOnDate(today);
        long availableToday = Math.max(0, activeRooms - occupiedToday);

        double occupancyRate = 0.0;
        if (activeRooms > 0) {
            double rate = ((double) occupiedToday / (double) activeRooms) * 100.0;
            occupancyRate = BigDecimal.valueOf(rate)
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        long pendingRequests = bookingRepository.countByStatus(BookingStatus.PENDING);

        // Bookings per month for the last 6 months (oldest to newest)
        List<MonthBookingCount> bookingsPerMonth = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = currentMonth.minusMonths(i);
            LocalDateTime start = ym.atDay(1).atStartOfDay();
            LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
            long count = bookingRepository.countByCreatedAtBetween(start, end);
            String label = ym.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH));
            bookingsPerMonth.add(new MonthBookingCount(label, count));
        }

        List<Booking> recentPending = bookingRepository.findRecentByStatus(
                BookingStatus.PENDING, PageRequest.of(0, 5)
        );

        return new AdminDashboardStats(
                totalRooms,
                activeRooms,
                occupiedToday,
                availableToday,
                occupancyRate,
                pendingRequests,
                bookingsPerMonth,
                recentPending
        );
    }

    /**
     * Builds personalized dashboard view for an authenticated customer.
     */
    public CustomerDashboardView customerView(String email) {
        LocalDate today = LocalDate.now();
        List<Booking> allUserBookings = bookingRepository.findByUserEmailOrderByCreatedAtDesc(email);

        // Upcoming bookings: PENDING or APPROVED with checkOut today or later, soonest first
        List<Booking> upcomingBookings = allUserBookings.stream()
                .filter(b -> (b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.APPROVED)
                        && b.getCheckOut() != null && !b.getCheckOut().isBefore(today))
                .sorted(Comparator.comparing(Booking::getCheckIn).thenComparing(Booking::getId))
                .toList();

        long upcomingCount = upcomingBookings.size();

        long pendingCount = allUserBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING)
                .count();

        long pastCount = allUserBookings.stream()
                .filter(b -> b.getCheckOut() != null && b.getCheckOut().isBefore(today))
                .count();

        Booking nextStay = upcomingBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.APPROVED)
                .findFirst()
                .orElse(null);

        return new CustomerDashboardView(
                upcomingCount,
                pendingCount,
                pastCount,
                nextStay,
                upcomingBookings
        );
    }
}

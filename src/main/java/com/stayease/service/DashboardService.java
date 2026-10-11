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
     * Calculates accurate financial, occupancy, booking, cancellation, and room-utilization
     * metrics from persisted database records across a specified date range [startDate, endDate].
     * Never calculates financial metrics from hardcoded sample data.
     */
    public com.stayease.dto.AdminAnalyticsReport generateAnalyticsReport(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusMonths(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now().plusMonths(1);
        if (!start.isBefore(end)) {
            // Swap or normalize if invalid
            LocalDate temp = start;
            start = end;
            end = temp.isEqual(end) ? end.plusDays(1) : temp;
        }

        List<Booking> bookingsInRange = bookingRepository.findBookingsInDateRange(start, end);
        List<com.stayease.model.Room> allRooms = roomRepository.findAllByOrderByRoomNumberAsc();

        long totalBookings = bookingsInRange.size();
        long approvedCount = 0;
        long checkedInCount = 0;
        long checkedOutCount = 0;
        long noShowCount = 0;
        long cancelledCount = 0;
        long rejectedCount = 0;

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal completedRevenue = BigDecimal.ZERO;

        long totalDaysInWindow = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start, end));
        java.util.Map<Long, Long> roomOccupiedNights = new java.util.HashMap<>();
        java.util.Map<Long, BigDecimal> roomRevenue = new java.util.HashMap<>();

        for (com.stayease.model.Room r : allRooms) {
            roomOccupiedNights.put(r.getId(), 0L);
            roomRevenue.put(r.getId(), BigDecimal.ZERO);
        }

        for (Booking b : bookingsInRange) {
            BookingStatus s = b.getStatus();
            if (s == BookingStatus.APPROVED) {
                approvedCount++;
            } else if (s == BookingStatus.CHECKED_IN) {
                checkedInCount++;
            } else if (s == BookingStatus.CHECKED_OUT) {
                checkedOutCount++;
            } else if (s == BookingStatus.NO_SHOW) {
                noShowCount++;
            } else if (s == BookingStatus.CANCELLED) {
                cancelledCount++;
            } else if (s == BookingStatus.REJECTED) {
                rejectedCount++;
            }

            // Financial and utilization calculation for valid stays (APPROVED, CHECKED_IN, CHECKED_OUT)
            if (s == BookingStatus.APPROVED || s == BookingStatus.CHECKED_IN || s == BookingStatus.CHECKED_OUT) {
                if (b.getTotalPrice() != null) {
                    totalRevenue = totalRevenue.add(b.getTotalPrice());
                }
                if (s == BookingStatus.CHECKED_OUT && b.getTotalPrice() != null) {
                    completedRevenue = completedRevenue.add(b.getTotalPrice());
                }

                // Overlap days with the report window [start, end]
                LocalDate stayStart = b.getCheckIn().isBefore(start) ? start : b.getCheckIn();
                LocalDate stayEnd = b.getCheckOut().isAfter(end) ? end : b.getCheckOut();
                if (stayStart.isBefore(stayEnd)) {
                    long nightsInWindow = java.time.temporal.ChronoUnit.DAYS.between(stayStart, stayEnd);
                    if (b.getRoom() != null) {
                        Long rId = b.getRoom().getId();
                        roomOccupiedNights.put(rId, roomOccupiedNights.getOrDefault(rId, 0L) + nightsInWindow);
                        if (b.getTotalPrice() != null) {
                            roomRevenue.put(rId, roomRevenue.getOrDefault(rId, BigDecimal.ZERO).add(b.getTotalPrice()));
                        }
                    }
                }
            }
        }

        double cancellationRate = 0.0;
        if (totalBookings > 0) {
            double rate = ((double) cancelledCount / (double) totalBookings) * 100.0;
            cancellationRate = BigDecimal.valueOf(rate).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }

        // Room utilization breakdown
        List<com.stayease.dto.AdminAnalyticsReport.RoomUtilizationStat> roomStats = new ArrayList<>();
        long totalRoomNightsPossible = allRooms.size() * totalDaysInWindow;
        long totalRoomNightsOccupied = 0;

        for (com.stayease.model.Room r : allRooms) {
            long nights = roomOccupiedNights.getOrDefault(r.getId(), 0L);
            totalRoomNightsOccupied += nights;
            double utilization = ((double) nights / (double) totalDaysInWindow) * 100.0;
            double roundedUtil = BigDecimal.valueOf(utilization).setScale(1, RoundingMode.HALF_UP).doubleValue();
            BigDecimal rev = roomRevenue.getOrDefault(r.getId(), BigDecimal.ZERO);
            roomStats.add(new com.stayease.dto.AdminAnalyticsReport.RoomUtilizationStat(
                    r.getId(),
                    r.getRoomNumber(),
                    r.getType() != null ? r.getType().name() : "STANDARD",
                    nights,
                    roundedUtil,
                    rev
            ));
        }

        double averageOccupancyRate = 0.0;
        if (totalRoomNightsPossible > 0) {
            double avgRate = ((double) totalRoomNightsOccupied / (double) totalRoomNightsPossible) * 100.0;
            averageOccupancyRate = BigDecimal.valueOf(avgRate).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }

        BigDecimal adr = BigDecimal.ZERO;
        if (totalRoomNightsOccupied > 0 && totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            adr = totalRevenue.divide(BigDecimal.valueOf(totalRoomNightsOccupied), 2, RoundingMode.HALF_UP);
        }

        BigDecimal revPar = BigDecimal.ZERO;
        if (totalRoomNightsPossible > 0 && totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            revPar = totalRevenue.divide(BigDecimal.valueOf(totalRoomNightsPossible), 2, RoundingMode.HALF_UP);
        }

        double averageLeadTimeDays = 0.0;
        long totalLeadDays = 0;
        long validLeadBookings = 0;
        for (Booking b : bookingsInRange) {
            if (b.getCreatedAt() != null && b.getCheckIn() != null) {
                LocalDate bookedDate = b.getCreatedAt().toLocalDate();
                if (!b.getCheckIn().isBefore(bookedDate)) {
                    totalLeadDays += java.time.temporal.ChronoUnit.DAYS.between(bookedDate, b.getCheckIn());
                    validLeadBookings++;
                }
            }
        }
        if (validLeadBookings > 0) {
            averageLeadTimeDays = BigDecimal.valueOf((double) totalLeadDays / (double) validLeadBookings)
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        com.stayease.dto.AdminAnalyticsReport report = new com.stayease.dto.AdminAnalyticsReport(
                start,
                end,
                totalBookings,
                approvedCount,
                checkedInCount,
                checkedOutCount,
                noShowCount,
                cancelledCount,
                rejectedCount,
                totalRevenue,
                completedRevenue,
                cancellationRate,
                averageOccupancyRate,
                roomStats
        );
        report.setAdr(adr);
        report.setRevPar(revPar);
        report.setAverageLeadTimeDays(averageLeadTimeDays);
        report.setTotalRoomNightsOccupied(totalRoomNightsOccupied);
        report.setTotalRoomNightsPossible(totalRoomNightsPossible);
        return report;
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

package com.stayease.dto;

import com.stayease.model.Booking;
import java.util.List;

/**
 * Aggregated administrative metrics and recent booking requests for the admin dashboard (Phase 13).
 */
public record AdminDashboardStats(
        long totalRooms,
        long activeRooms,
        long occupiedToday,
        long availableToday,
        double occupancyRate,
        long pendingRequests,
        List<MonthBookingCount> bookingsPerMonth,
        List<Booking> recentPending
) {}

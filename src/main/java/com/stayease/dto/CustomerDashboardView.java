package com.stayease.dto;

import com.stayease.model.Booking;
import java.util.List;

/**
 * Customer dashboard metrics and personalized stay overview (Phase 13).
 */
public record CustomerDashboardView(
        long upcomingCount,
        long pendingCount,
        long pastCount,
        Booking nextStay,
        List<Booking> upcomingBookings
) {}

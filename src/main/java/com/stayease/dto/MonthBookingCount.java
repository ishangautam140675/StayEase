package com.stayease.dto;

/**
 * Represents the count of bookings created within a specific month.
 */
public record MonthBookingCount(String monthLabel, long count) {}

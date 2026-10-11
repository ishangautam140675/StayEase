package com.stayease.service;

import com.stayease.model.Booking;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service generating CSV exports for administrative booking reports.
 */
@Service
public class BookingExportService {

    public static final String CSV_HEADER = "Booking ID,Customer name,Customer email,Room,Check-in,Checkout,Guests,Status,Total price,Requested on";
    public static final String DETAILED_CSV_HEADER = "Booking ID,Booking reference,Customer name,Customer email,Room,Check-in,Checkout,Guests,Status,Total price,Requested on,Operational notes";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Converts a list of {@link Booking} entities to a CSV formatted string.
     * Preserves exact 10-column header for backward compatibility with existing automated systems.
     *
     * Rules:
     * 1. Prefix formula trigger characters (=, +, -, @, \t, \r, |) with a single quote to prevent spreadsheet formula injection.
     * 2. Wrap fields containing comma, quote, or line break (\n, \r) in double quotes and double any internal quotes.
     */
    public String toCsv(List<Booking> bookings) {
        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER).append("\r\n");

        if (bookings != null) {
            for (Booking b : bookings) {
                String bookingId = b.getId() != null ? String.valueOf(b.getId()) : "";
                String customerName = b.getUser() != null && b.getUser().getFullName() != null ? b.getUser().getFullName() : "";
                String customerEmail = b.getUser() != null && b.getUser().getEmail() != null ? b.getUser().getEmail() : "";
                String room = b.getRoom() != null && b.getRoom().getRoomNumber() != null ? b.getRoom().getRoomNumber() : "";
                String checkIn = b.getCheckIn() != null ? b.getCheckIn().toString() : "";
                String checkOut = b.getCheckOut() != null ? b.getCheckOut().toString() : "";
                String guests = String.valueOf(b.getGuests());
                String status = b.getStatus() != null ? b.getStatus().name() : "";
                BigDecimal totalPrice = b.getTotalPrice() != null ? b.getTotalPrice() : BigDecimal.ZERO;
                String totalPriceStr = totalPrice.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
                String requestedOn = b.getCreatedAt() != null ? b.getCreatedAt().format(DATE_TIME_FORMATTER) : "";

                sb.append(escapeCsvField(bookingId)).append(",")
                  .append(escapeCsvField(customerName)).append(",")
                  .append(escapeCsvField(customerEmail)).append(",")
                  .append(escapeCsvField(room)).append(",")
                  .append(escapeCsvField(checkIn)).append(",")
                  .append(escapeCsvField(checkOut)).append(",")
                  .append(escapeCsvField(guests)).append(",")
                  .append(escapeCsvField(status)).append(",")
                  .append(escapeCsvField(totalPriceStr)).append(",")
                  .append(escapeCsvField(requestedOn))
                  .append("\r\n");
            }
        }

        return sb.toString();
    }

    /**
     * Converts bookings to detailed operational CSV with booking references and operational notes.
     */
    public String toDetailedCsv(List<Booking> bookings) {
        StringBuilder sb = new StringBuilder();
        sb.append(DETAILED_CSV_HEADER).append("\r\n");

        if (bookings != null) {
            for (Booking b : bookings) {
                String bookingId = b.getId() != null ? String.valueOf(b.getId()) : "";
                String bookingRef = b.getBookingReference() != null ? b.getBookingReference() : "";
                String customerName = b.getUser() != null && b.getUser().getFullName() != null ? b.getUser().getFullName() : "";
                String customerEmail = b.getUser() != null && b.getUser().getEmail() != null ? b.getUser().getEmail() : "";
                String room = b.getRoom() != null && b.getRoom().getRoomNumber() != null ? b.getRoom().getRoomNumber() : "";
                String checkIn = b.getCheckIn() != null ? b.getCheckIn().toString() : "";
                String checkOut = b.getCheckOut() != null ? b.getCheckOut().toString() : "";
                String guests = String.valueOf(b.getGuests());
                String status = b.getStatus() != null ? b.getStatus().name() : "";
                BigDecimal totalPrice = b.getTotalPrice() != null ? b.getTotalPrice() : BigDecimal.ZERO;
                String totalPriceStr = totalPrice.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
                String requestedOn = b.getCreatedAt() != null ? b.getCreatedAt().format(DATE_TIME_FORMATTER) : "";
                String operationalNotes = b.getOperationalNotes() != null ? b.getOperationalNotes() : "";

                sb.append(escapeCsvField(bookingId)).append(",")
                  .append(escapeCsvField(bookingRef)).append(",")
                  .append(escapeCsvField(customerName)).append(",")
                  .append(escapeCsvField(customerEmail)).append(",")
                  .append(escapeCsvField(room)).append(",")
                  .append(escapeCsvField(checkIn)).append(",")
                  .append(escapeCsvField(checkOut)).append(",")
                  .append(escapeCsvField(guests)).append(",")
                  .append(escapeCsvField(status)).append(",")
                  .append(escapeCsvField(totalPriceStr)).append(",")
                  .append(escapeCsvField(requestedOn)).append(",")
                  .append(escapeCsvField(operationalNotes))
                  .append("\r\n");
            }
        }

        return sb.toString();
    }

    /**
     * Escapes a single CSV value:
     * - Null is returned as empty string.
     * - Formula protection: if starts with '=', '+', '-', '@', '\t', '\r', or '|', prefixes with single quote "'".
     * - RFC 4180 escaping: if contains ',', '"', '\n', or '\r', wraps in double quotes and replaces '"' with '""'.
     */
    public String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }

        String sanitized = value;
        // Formula injection mitigation for spreadsheet software
        if (!sanitized.isEmpty()) {
            char firstChar = sanitized.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@'
                    || firstChar == '\t' || firstChar == '\r' || firstChar == '|') {
                sanitized = "'" + sanitized;
            }
        }

        // Standard CSV escaping for comma, double quotes, newline, or carriage return
        boolean needsQuotes = sanitized.contains(",")
                || sanitized.contains("\"")
                || sanitized.contains("\n")
                || sanitized.contains("\r");

        if (needsQuotes) {
            sanitized = "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }

        return sanitized;
    }
}

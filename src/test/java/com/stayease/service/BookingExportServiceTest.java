package com.stayease.service;

import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookingExportServiceTest {

    private BookingExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new BookingExportService();
    }

    @Test
    @DisplayName("Header row matches exact specification")
    void headerRowIsCorrect() {
        String csv = exportService.toCsv(Collections.emptyList());
        String expectedHeader = "Booking ID,Customer name,Customer email,Room,Check-in,Checkout,Guests,Status,Total price,Requested on";
        assertThat(csv.trim()).isEqualTo(expectedHeader);
    }

    @Test
    @DisplayName("Escapes customer name with a comma correctly in double quotes")
    void customerNameWithCommaIsEscaped() {
        User user = new User("Doe, Jane", "jane@example.com", "pass", "1234567890", Role.CUSTOMER);
        Room room = new Room("101", RoomType.SINGLE, 1, BigDecimal.valueOf(100), "Standard", true);
        Booking booking = new Booking(user, room, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), 1, BookingStatus.APPROVED, null);
        booking.setId(10L);
        booking.setCreatedAt(LocalDateTime.of(2026, 10, 11, 14, 30));

        String csv = exportService.toCsv(List.of(booking));
        assertThat(csv).contains("\"Doe, Jane\"");
    }

    @Test
    @DisplayName("Escapes customer name with quotes by doubling any quote inside")
    void customerNameWithQuoteIsEscaped() {
        User user = new User("O'Connor \"The Boss\"", "boss@example.com", "pass", "1234567890", Role.CUSTOMER);
        Room room = new Room("102", RoomType.DOUBLE, 2, BigDecimal.valueOf(150), "Deluxe", true);
        Booking booking = new Booking(user, room, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 7), 2, BookingStatus.PENDING, null);
        booking.setId(11L);
        booking.setCreatedAt(LocalDateTime.of(2026, 10, 11, 15, 0));

        String csv = exportService.toCsv(List.of(booking));
        // Inside double quotes, " becomes ""
        assertThat(csv).contains("\"O'Connor \"\"The Boss\"\"\"");
    }

    @Test
    @DisplayName("Neutralizes formula injection starting with =, +, -, or @ by prefixing with single quote")
    void customerNameStartingWithFormulaTriggersIsNeutralized() {
        User userFormulaEq = new User("=SUM(A1:A10)", "formula@example.com", "pass", "123", Role.CUSTOMER);
        User userFormulaPlus = new User("+cmd|' /C calc'!A0", "calc@example.com", "pass", "123", Role.CUSTOMER);
        User userFormulaMinus = new User("-10+20", "minus@example.com", "pass", "123", Role.CUSTOMER);
        User userFormulaAt = new User("@something", "at@example.com", "pass", "123", Role.CUSTOMER);

        Room room = new Room("201", RoomType.SINGLE, 1, BigDecimal.valueOf(80), "Cozy", true);

        Booking b1 = new Booking(userFormulaEq, room, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2), 1, BookingStatus.PENDING, null);
        b1.setId(1L);
        b1.setCreatedAt(LocalDateTime.of(2026, 10, 11, 10, 0));

        Booking b2 = new Booking(userFormulaPlus, room, LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 3), 1, BookingStatus.PENDING, null);
        b2.setId(2L);
        b2.setCreatedAt(LocalDateTime.of(2026, 10, 11, 10, 0));

        Booking b3 = new Booking(userFormulaMinus, room, LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 4), 1, BookingStatus.PENDING, null);
        b3.setId(3L);
        b3.setCreatedAt(LocalDateTime.of(2026, 10, 11, 10, 0));

        Booking b4 = new Booking(userFormulaAt, room, LocalDate.of(2026, 12, 4), LocalDate.of(2026, 12, 5), 1, BookingStatus.PENDING, null);
        b4.setId(4L);
        b4.setCreatedAt(LocalDateTime.of(2026, 10, 11, 10, 0));

        String csv = exportService.toCsv(List.of(b1, b2, b3, b4));

        assertThat(csv).contains("'=SUM(A1:A10)");
        assertThat(csv).contains("'+cmd|' /C calc'!A0");
        assertThat(csv).contains("'-10+20");
        assertThat(csv).contains("'@something");
    }

    @Test
    @DisplayName("Calculates total price (pricePerNight * nights) correctly and formats dates")
    void formatsCompleteRowData() {
        User user = new User("Alice Smith", "alice@example.com", "pass", "123", Role.CUSTOMER);
        Room room = new Room("305", RoomType.DOUBLE, 2, BigDecimal.valueOf(250.50), "Suite", true);
        Booking booking = new Booking(user, room, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4), 2, BookingStatus.APPROVED, null);
        booking.setId(99L);
        booking.setCreatedAt(LocalDateTime.of(2026, 10, 11, 12, 45));

        String csv = exportService.toCsv(List.of(booking));
        // 3 nights * 250.50 = 751.50
        assertThat(csv).contains("99,Alice Smith,alice@example.com,305,2026-11-01,2026-11-04,2,APPROVED,751.50,2026-10-11 12:45");
    }
}

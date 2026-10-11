package com.stayease.service;

import com.stayease.dto.AdminAnalyticsReport;
import com.stayease.exception.BookingConflictException;
import com.stayease.exception.InvalidBookingStateException;
import com.stayease.model.AdminAuditLog;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomMaintenanceSchedule;
import com.stayease.model.RoomType;
import com.stayease.model.SeasonalPriceRule;
import com.stayease.model.User;
import com.stayease.repository.AdminAuditLogRepository;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomMaintenanceScheduleRepository;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.SeasonalPriceRuleRepository;
import com.stayease.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("default")
@Transactional
public class Phase5PropertyOperationsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private MaintenanceScheduleService maintenanceService;

    @Autowired
    private SeasonalPricingService seasonalPricingService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private BookingExportService bookingExportService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomMaintenanceScheduleRepository maintenanceRepository;

    @Autowired
    private SeasonalPriceRuleRepository seasonalRepository;

    @Autowired
    private AdminAuditLogRepository auditLogRepository;

    private User testCustomer;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        testCustomer = userRepository.findByEmail("phase5_guest@example.com")
                .orElseGet(() -> userRepository.save(new User("Phase5 Guest", "phase5_guest@example.com", "Secret@123", "9876543210", Role.CUSTOMER)));

        testRoom = roomRepository.findByRoomNumberIgnoreCase("P5-101")
                .orElseGet(() -> roomRepository.save(new Room("P5-101", RoomType.DOUBLE, 2, BigDecimal.valueOf(2000.00), "Phase 5 Test Suite", true)));
    }

    // ── 1. Reporting calculations and date boundaries from persisted data ──

    @Test
    @DisplayName("Analytics calculates revenue, occupancy, and cancellation rates strictly from persisted records")
    void reportingCalculationsFromPersistedData() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 10);

        // Booking 1: Approved, 3 nights * 2000 = 6000
        Booking b1 = new Booking(testCustomer, testRoom, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5), 2, BookingStatus.APPROVED, null);
        b1.setPricePerNightAtBooking(BigDecimal.valueOf(2000.00));
        b1.setTotalPrice(BigDecimal.valueOf(6000.00));
        bookingRepository.save(b1);

        // Booking 2: Checked out, 2 nights * 2000 = 4000
        Booking b2 = new Booking(testCustomer, testRoom, LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 8), 2, BookingStatus.CHECKED_OUT, null);
        b2.setPricePerNightAtBooking(BigDecimal.valueOf(2000.00));
        b2.setTotalPrice(BigDecimal.valueOf(4000.00));
        bookingRepository.save(b2);

        // Booking 3: Cancelled
        Booking b3 = new Booking(testCustomer, testRoom, LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10), 2, BookingStatus.CANCELLED, null);
        b3.setTotalPrice(BigDecimal.valueOf(4000.00));
        bookingRepository.save(b3);

        AdminAnalyticsReport report = dashboardService.generateAnalyticsReport(start, end);

        assertThat(report.getTotalBookings()).isGreaterThanOrEqualTo(3);
        assertThat(report.getApprovedBookings()).isGreaterThanOrEqualTo(1);
        assertThat(report.getCheckedOutBookings()).isGreaterThanOrEqualTo(1);
        assertThat(report.getCancelledBookings()).isGreaterThanOrEqualTo(1);

        // Revenue must sum accurately from persisted data, never hardcoded
        assertThat(report.getTotalRevenue()).isGreaterThanOrEqualTo(BigDecimal.valueOf(10000.00));
        assertThat(report.getCompletedRevenue()).isGreaterThanOrEqualTo(BigDecimal.valueOf(4000.00));
        assertThat(report.getCancellationRate()).isGreaterThan(0.0);

        // Verify room utilization breakdown exists for the test room
        AdminAnalyticsReport.RoomUtilizationStat stat = report.getRoomUtilizations().stream()
                .filter(u -> u.getRoomNumber().equalsIgnoreCase("P5-101"))
                .findFirst()
                .orElse(null);

        assertThat(stat).isNotNull();
        assertThat(stat.getNightsOccupied()).isEqualTo(5); // 3 + 2 nights
        assertThat(stat.getRevenue()).isEqualTo(BigDecimal.valueOf(10000.00));
    }

    // ── 2. Validated Status Transitions: Check-in, Check-out, No-Show ───────

    @Test
    @DisplayName("Status transition workflow: APPROVED -> CHECKED_IN -> CHECKED_OUT")
    void checkInAndCheckOutWorkflow() {
        Booking booking = new Booking(testCustomer, testRoom, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 2, BookingStatus.PENDING, null);
        booking.setTotalPrice(BigDecimal.valueOf(4000.00));
        booking = bookingRepository.save(booking);

        // PENDING cannot check in
        final Long bId = booking.getId();
        assertThatThrownBy(() -> bookingService.checkIn(bId, "admin@stayease.com"))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("only APPROVED bookings can be checked in");

        // Approve
        booking = bookingService.approve(bId, "admin@stayease.com");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.APPROVED);

        // Check In
        booking = bookingService.checkIn(bId, "admin@stayease.com");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(booking.getCheckedInAt()).isNotNull();

        // Check Out
        booking = bookingService.checkOut(bId, "admin@stayease.com");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHECKED_OUT);
        assertThat(booking.getCheckedOutAt()).isNotNull();

        // Already checked out cannot check in again
        assertThatThrownBy(() -> bookingService.checkIn(bId, "admin@stayease.com"))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    @DisplayName("Status transition workflow: APPROVED -> NO_SHOW")
    void noShowWorkflow() {
        Booking booking = new Booking(testCustomer, testRoom, LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 7), 2, BookingStatus.APPROVED, null);
        booking.setTotalPrice(BigDecimal.valueOf(4000.00));
        booking = bookingRepository.save(booking);

        booking = bookingService.markNoShow(booking.getId(), "admin@stayease.com", "Guest did not arrive after 24h grace period");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.NO_SHOW);
        assertThat(booking.getAdminRemark()).isEqualTo("Guest did not arrive after 24h grace period");
        assertThat(booking.getStatus().isBlocking()).isFalse(); // Released inventory
    }

    // ── 3. Room Maintenance Scheduling and Overlap Conflict Rejection ──────

    @Test
    @DisplayName("Maintenance block is rejected if active confirmed booking overlaps the period")
    void maintenanceSchedulingRejectsActiveBookingConflict() {
        LocalDate start = LocalDate.of(2026, 12, 10);
        LocalDate end = LocalDate.of(2026, 12, 15);

        Booking booking = new Booking(testCustomer, testRoom, LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 14), 2, BookingStatus.APPROVED, null);
        booking.setTotalPrice(BigDecimal.valueOf(4000.00));
        bookingRepository.save(booking);

        // Attempt to schedule maintenance over the confirmed booking dates
        assertThatThrownBy(() -> maintenanceService.scheduleMaintenance(
                testRoom.getId(), start, end, "AC unit overhaul", "admin@stayease.com"))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("active booking(s) already exist");
    }

    @Test
    @DisplayName("Maintenance scheduling succeeds on open inventory and blocks customer search availability")
    void maintenanceBlocksCustomerInventoryAvailability() {
        LocalDate start = LocalDate.of(2026, 12, 20);
        LocalDate end = LocalDate.of(2026, 12, 25);

        RoomMaintenanceSchedule schedule = maintenanceService.scheduleMaintenance(
                testRoom.getId(), start, end, "Deep repainting", "admin@stayease.com");

        assertThat(schedule.getId()).isNotNull();

        // Room should NOT appear in search for dates overlapping maintenance
        List<Room> available = roomRepository.findAvailableRooms(
                LocalDate.of(2026, 12, 21),
                LocalDate.of(2026, 12, 23),
                1,
                BookingStatus.BLOCKING
        );

        assertThat(available).noneMatch(r -> r.getId().equals(testRoom.getId()));

        // Delete maintenance schedule
        maintenanceService.deleteMaintenanceSchedule(schedule.getId(), "admin@stayease.com");

        // Now room becomes available again
        List<Room> availableAfter = roomRepository.findAvailableRooms(
                LocalDate.of(2026, 12, 21),
                LocalDate.of(2026, 12, 23),
                1,
                BookingStatus.BLOCKING
        );

        assertThat(availableAfter).anyMatch(r -> r.getId().equals(testRoom.getId()));
    }

    @Test
    @DisplayName("Booking creation fails with clear notification when stay dates overlap scheduled maintenance")
    void bookingCreationFailsWhenOverlappingScheduledMaintenance() {
        LocalDate maintStart = LocalDate.of(2026, 12, 10);
        LocalDate maintEnd = LocalDate.of(2026, 12, 15);

        maintenanceService.scheduleMaintenance(
                testRoom.getId(), maintStart, maintEnd, "HVAC overhaul", "admin@stayease.com");

        com.stayease.dto.BookingForm form = new com.stayease.dto.BookingForm(
                testRoom.getId(),
                LocalDate.of(2026, 12, 12),
                LocalDate.of(2026, 12, 16),
                2,
                "Special request"
        );

        assertThatThrownBy(() -> bookingService.createBooking(testCustomer.getEmail(), form))
                .isInstanceOf(com.stayease.exception.BookingValidationException.class)
                .hasMessageContaining("under scheduled maintenance")
                .hasMessageContaining("HVAC overhaul");
    }

    // ── 4. Configurable Seasonal Pricing and Historical Preservation ───────

    @Test
    @DisplayName("Seasonal pricing modifies rates dynamically for new stays while preserving past bookings")
    void seasonalPricingCalculationAndRatePreservation() {
        LocalDate stayStart = LocalDate.of(2026, 12, 25);
        LocalDate stayEnd = LocalDate.of(2026, 12, 28); // 3 nights

        // 1. Booking made at base rate (2000.00)
        Booking pastBooking = new Booking(testCustomer, testRoom, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4), 2, BookingStatus.APPROVED, null);
        pastBooking.setPricePerNightAtBooking(BigDecimal.valueOf(2000.00));
        pastBooking.setTotalPrice(BigDecimal.valueOf(6000.00));
        pastBooking = bookingRepository.save(pastBooking);

        // 2. Create seasonal surge rule (+50% via 1.50 multiplier) for Christmas week
        SeasonalPriceRule rule = seasonalPricingService.createRule(
                "Christmas Peak Surge",
                testRoom.getId(),
                null,
                LocalDate.of(2026, 12, 20),
                LocalDate.of(2026, 12, 31),
                null,
                BigDecimal.valueOf(1.50),
                "admin@stayease.com"
        );

        assertThat(rule.getId()).isNotNull();

        // Effective rate for Christmas stay should be 2000 * 1.50 = 3000.00
        BigDecimal surgeRate = seasonalPricingService.calculateStayAverageRate(testRoom, stayStart, stayEnd);
        assertThat(surgeRate).isEqualByComparingTo(BigDecimal.valueOf(3000.00));

        // Past booking prices remain completely unchanged (historical preservation)
        Booking reloadedPast = bookingRepository.findById(pastBooking.getId()).orElseThrow();
        assertThat(reloadedPast.getPricePerNightAtBooking()).isEqualByComparingTo(BigDecimal.valueOf(2000.00));
        assertThat(reloadedPast.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(6000.00));
    }

    // ── 5. Auditable Administrative Action Logging ─────────────────────────

    @Test
    @DisplayName("Admin operations create persistent audit records")
    void administrativeActionAuditLogging() {
        long initialCount = auditLogRepository.count();

        auditLogService.log("ops_admin@stayease.com", "ROOM_PRICE_UPDATE", "ROOM", String.valueOf(testRoom.getId()), "Updated nightly price to 2500");

        Page<AdminAuditLog> logs = auditLogService.getAuditLogs(PageRequest.of(0, 10));
        assertThat(logs.getTotalElements()).isEqualTo(initialCount + 1);

        AdminAuditLog latest = logs.getContent().get(0);
        assertThat(latest.getAdminEmail()).isEqualTo("ops_admin@stayease.com");
        assertThat(latest.getAction()).isEqualTo("ROOM_PRICE_UPDATE");
        assertThat(latest.getEntityType()).isEqualTo("ROOM");
        assertThat(latest.getDetails()).contains("Updated nightly price to 2500");
    }

    // ── 6. Operational Notes and Guest Search ──────────────────────────────

    @Test
    @DisplayName("Internal operational notes can be updated and queried")
    void operationalNotesCanBeUpdated() {
        Booking booking = new Booking(testCustomer, testRoom, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 2, BookingStatus.APPROVED, null);
        booking = bookingRepository.save(booking);

        Booking updated = bookingService.updateOperationalNotes(booking.getId(), "Late arrival requested; VIP guest welcome basket", "admin@stayease.com");

        assertThat(updated.getOperationalNotes()).isEqualTo("Late arrival requested; VIP guest welcome basket");

        // Reload from repository to ensure persistence
        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloaded.getOperationalNotes()).isEqualTo("Late arrival requested; VIP guest welcome basket");
    }

    // ── 7. CSV Exports with formula injection defense ──────────────────────

    @Test
    @DisplayName("CSV export neutralizes tab, return, pipe, and formula trigger characters")
    void csvExportNeutralizesFormulaTriggers() {
        User maliciousUser = new User("=SUM(1,2)", "mal@test.com", "pass", "123", Role.CUSTOMER);
        Booking b = new Booking(maliciousUser, testRoom, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), 1, BookingStatus.APPROVED, null);
        b.setOperationalNotes("@evil_command");

        String csv = bookingExportService.toDetailedCsv(List.of(b));

        // Must prefix with single quote to prevent spreadsheet execution
        assertThat(csv).contains("'=SUM(1,2)");
        assertThat(csv).contains("'@evil_command");

        // Verify direct formula injection characters on escapeCsvField
        assertThat(bookingExportService.escapeCsvField("=HYPERLINK(\"http://evil.com\")")).isEqualTo("\"'=HYPERLINK(\"\"http://evil.com\"\")\"");
        assertThat(bookingExportService.escapeCsvField("+1234")).isEqualTo("'+1234");
        assertThat(bookingExportService.escapeCsvField("-5678")).isEqualTo("'-5678");
        assertThat(bookingExportService.escapeCsvField("|pipe")).isEqualTo("'|pipe");
        assertThat(bookingExportService.escapeCsvField("\ttab")).isEqualTo("'\ttab");
    }

    // ── 8. Security & Role Permissions: Customers blocked from Admin Console ──

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    @DisplayName("Customers are blocked with HTTP 403 Forbidden from accessing admin operations")
    void customersCannotAccessAdminOperations() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/bookings"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/operations/maintenance"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/operations/pricing"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/operations/audit-logs"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/operations/maintenance/schedule")
                        .param("roomId", String.valueOf(testRoom.getId()))
                        .param("startDate", "2026-12-01")
                        .param("endDate", "2026-12-05")
                        .param("reason", "Unauthorized attempt"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("Administrators have full access to management console routes")
    void administratorsHaveAccessToOperations() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/bookings"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/operations/maintenance"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/operations/pricing"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/operations/audit-logs"))
                .andExpect(status().isOk());
    }
}

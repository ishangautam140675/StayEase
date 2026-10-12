package com.stayease.service;

import com.stayease.dto.AdminAnalyticsReport;
import com.stayease.model.AdminAuditLog;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class Phase10AnalyticsAndAuditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private BookingExportService exportService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Room testRoom1;
    private Room testRoom2;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByEmail("phase10guest@stayease.com").orElseGet(() -> {
            User user = new User("Phase Ten Guest", "phase10guest@stayease.com", "$2a$10$DUMMYPASSWORDHASH", "9876543210", Role.CUSTOMER);
            user.setEmailVerified(true);
            return userRepository.save(user);
        });

        testRoom1 = roomRepository.findByRoomNumberIgnoreCase("P10-101").orElseGet(() -> {
            Room room = new Room("P10-101", RoomType.SINGLE, 1, new BigDecimal("2000.00"), "Room 101", true);
            return roomRepository.save(room);
        });

        testRoom2 = roomRepository.findByRoomNumberIgnoreCase("P10-102").orElseGet(() -> {
            Room room = new Room("P10-102", RoomType.DOUBLE, 2, new BigDecimal("4000.00"), "Room 102", true);
            return roomRepository.save(room);
        });
    }

    @Test
    @DisplayName("Analytics: Correctly calculates ADR (Average Daily Rate) and RevPAR")
    void analytics_CalculatesAccurateAdrAndRevPar() {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(4); // 4 days window

        // Booking on room 1: 2 nights at 2000 = 4000
        Booking b1 = new Booking(testUser, testRoom1, start, start.plusDays(2), 1, BookingStatus.APPROVED, null);
        b1.setBookingReference("P10-BK-001");
        bookingRepository.save(b1);

        AdminAnalyticsReport report = dashboardService.generateAnalyticsReport(start, end);
        assertThat(report).isNotNull();
        assertThat(report.getTotalBookings()).isGreaterThanOrEqualTo(1);

        // Occupied room nights = 2
        assertThat(report.getTotalRoomNightsOccupied()).isGreaterThanOrEqualTo(2);
        // Total possible room nights = activeRooms * 4 days
        assertThat(report.getTotalRoomNightsPossible()).isGreaterThan(0);

        // ADR = totalRevenue / totalRoomNightsOccupied
        assertThat(report.getAdr()).isGreaterThan(BigDecimal.ZERO);
        // RevPAR = totalRevenue / totalRoomNightsPossible
        assertThat(report.getRevPar()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Analytics: Zero bookings in window gracefully outputs zero ADR, RevPAR, and occupancy without errors")
    void analytics_ZeroBookings_GracefulZeros() {
        LocalDate farFutureStart = LocalDate.now().plusYears(5);
        LocalDate farFutureEnd = farFutureStart.plusDays(7);

        AdminAnalyticsReport report = dashboardService.generateAnalyticsReport(farFutureStart, farFutureEnd);
        assertThat(report.getTotalBookings()).isEqualTo(0);
        assertThat(report.getTotalRevenue()).isEqualTo(BigDecimal.ZERO);
        assertThat(report.getAdr()).isEqualTo(BigDecimal.ZERO);
        assertThat(report.getRevPar()).isEqualTo(BigDecimal.ZERO);
        assertThat(report.getAverageOccupancyRate()).isEqualTo(0.0);
        assertThat(report.getAverageLeadTimeDays()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Audit Log Service: Records, searches with filters, and retrieves unpaginated export logs")
    void auditLog_RecordsAndSearches() {
        auditLogService.log("admin@stayease.com", "ROOM_PRICE_UPDATE", "ROOM", "P10-101", "Changed price to 2500");
        auditLogService.log("manager@stayease.com", "MAINTENANCE_SCHEDULED", "ROOM", "P10-102", "Painting work");

        Page<AdminAuditLog> paged = auditLogService.searchAuditLogs("admin@stayease.com", "ROOM_PRICE_UPDATE", null, null, PageRequest.of(0, 10));
        assertThat(paged.getContent()).isNotEmpty();
        assertThat(paged.getContent().get(0).getAction()).isEqualTo("ROOM_PRICE_UPDATE");

        List<AdminAuditLog> exportList = auditLogService.searchAuditLogsForExport("admin@stayease.com", "ROOM_PRICE_UPDATE", null, null);
        assertThat(exportList).isNotEmpty();
        assertThat(exportList.get(0).getDetails()).contains("Changed price to 2500");
    }

    @Test
    @DisplayName("Export Service: Generates valid CSV strings for Analytics Report and Audit Logs")
    void exportService_GeneratesValidCsvs() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(7);
        AdminAnalyticsReport report = dashboardService.generateAnalyticsReport(start, end);

        String analyticsCsv = exportService.analyticsReportToCsv(report);
        assertThat(analyticsCsv).contains("StayEase Operational & Financial Report");
        assertThat(analyticsCsv).contains("Average Daily Rate - ADR (INR)");
        assertThat(analyticsCsv).contains("Revenue Per Available Room - RevPAR (INR)");

        AdminAuditLog log = auditLogService.log("auditor@stayease.com", "TEST_ACTION", "BOOKING", "101", "Test audit entry");
        String auditCsv = exportService.auditLogsToCsv(List.of(log));
        assertThat(auditCsv).contains("Audit ID,Timestamp,Administrator,Action,Entity Type,Entity ID,Details");
        assertThat(auditCsv).contains("auditor@stayease.com");
        assertThat(auditCsv).contains("TEST_ACTION");
    }

    @Test
    @DisplayName("Endpoints: Admin can download Analytics CSV and Audit Logs CSV")
    @WithMockUser(username = "admin@stayease.com", roles = "ADMIN")
    void endpoints_AdminDownloadCsvs() throws Exception {
        mockMvc.perform(get("/admin/reports/export/csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().exists("Content-Disposition"));

        mockMvc.perform(get("/admin/operations/audit-logs/export/csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @DisplayName("Security: Customers are forbidden from downloading admin CSV exports")
    @WithMockUser(username = "phase10guest@stayease.com", roles = "CUSTOMER")
    void security_CustomersForbiddenFromExports() throws Exception {
        mockMvc.perform(get("/admin/reports/export/csv"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/operations/audit-logs/export/csv"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin: Delete individual audit log entry")
    @WithMockUser(username = "admin@stayease.com", roles = "ADMIN")
    void adminCanDeleteIndividualAuditLog() throws Exception {
        AdminAuditLog log = auditLogService.log("admin@stayease.com", "TEST_DELETE", "ROOM", "201", "Testing single delete");
        Long logId = log.getId();

        mockMvc.perform(post("/admin/operations/audit-logs/" + logId + "/delete")
                        .with(csrf())
                        .param("redirectUrl", "/admin/operations/audit-logs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/operations/audit-logs"))
                .andExpect(flash().attributeExists("successMessage"));

        Page<AdminAuditLog> remaining = auditLogService.getAuditLogs(PageRequest.of(0, 50));
        assertThat(remaining.getContent().stream().noneMatch(l -> l.getId().equals(logId))).isTrue();
    }

    @Test
    @DisplayName("Admin: Bulk delete selected audit log entries")
    @WithMockUser(username = "admin@stayease.com", roles = "ADMIN")
    void adminCanBulkDeleteAuditLogs() throws Exception {
        AdminAuditLog log1 = auditLogService.log("admin@stayease.com", "BULK_1", "ROOM", "201", "Bulk test 1");
        AdminAuditLog log2 = auditLogService.log("admin@stayease.com", "BULK_2", "ROOM", "202", "Bulk test 2");

        mockMvc.perform(post("/admin/operations/audit-logs/bulk-delete")
                        .with(csrf())
                        .param("logIds", String.valueOf(log1.getId()), String.valueOf(log2.getId()))
                        .param("redirectUrl", "/admin/operations/audit-logs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/operations/audit-logs"))
                .andExpect(flash().attributeExists("successMessage"));

        Page<AdminAuditLog> remaining = auditLogService.getAuditLogs(PageRequest.of(0, 50));
        assertThat(remaining.getContent().stream().noneMatch(l -> l.getId().equals(log1.getId()) || l.getId().equals(log2.getId()))).isTrue();
    }

    @Test
    @DisplayName("Admin: Clear all audit log entries")
    @WithMockUser(username = "admin@stayease.com", roles = "ADMIN")
    void adminCanClearAllAuditLogs() throws Exception {
        auditLogService.log("admin@stayease.com", "CLEAR_TEST", "ROOM", "201", "Testing clear all");

        mockMvc.perform(post("/admin/operations/audit-logs/clear-all")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/operations/audit-logs"))
                .andExpect(flash().attributeExists("successMessage"));

        Page<AdminAuditLog> remaining = auditLogService.getAuditLogs(PageRequest.of(0, 50));
        assertThat(remaining.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("Security: Customers are forbidden from deleting audit logs")
    @WithMockUser(username = "phase10guest@stayease.com", roles = "CUSTOMER")
    void security_CustomersForbiddenFromDeletingAuditLogs() throws Exception {
        AdminAuditLog log = auditLogService.log("admin@stayease.com", "SEC_TEST", "ROOM", "201", "Security test");

        mockMvc.perform(post("/admin/operations/audit-logs/" + log.getId() + "/delete").with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/operations/audit-logs/clear-all").with(csrf()))
                .andExpect(status().isForbidden());
    }
}

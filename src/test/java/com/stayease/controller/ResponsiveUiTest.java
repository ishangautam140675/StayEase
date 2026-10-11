package com.stayease.controller;

import com.stayease.model.*;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 8 Verification Tests: Multi-Device, Cross-Browser, and Responsive UI Readiness.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResponsiveUiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User customer;
    private Room room;
    private Booking booking;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setEmail("customer-responsive@example.com");
        customer.setPasswordHash(passwordEncoder.encode("password123"));
        customer.setFullName("Responsive Test User");
        customer.setRole(Role.CUSTOMER);
        userRepository.save(customer);

        room = new Room();
        room.setRoomNumber("RESP-101");
        room.setType(RoomType.SINGLE);
        room.setPricePerNight(new BigDecimal("1200.00"));
        room.setCapacity(1);
        room.setDescription("Responsive test single suite");
        room.setActive(true);
        roomRepository.save(room);

        booking = new Booking();
        booking.setUser(customer);
        booking.setRoom(room);
        booking.setCheckIn(LocalDate.now().plusDays(2));
        booking.setCheckOut(LocalDate.now().plusDays(5));
        booking.setGuests(1);
        booking.setStatus(BookingStatus.APPROVED);
        booking.setPaymentStatus(PaymentStatus.SUCCESSFUL);
        booking.setTotalPrice(new BigDecimal("3600.00"));
        booking.setBookingReference("RESP-SE-12345");
        bookingRepository.save(booking);
    }

    @Test
    @DisplayName("Home Page includes responsive viewport meta tag, modern stylesheets, and mobile navbar toggler")
    void homePage_hasViewportMetaAndBootstrapAssets() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"")))
                .andExpect(content().string(containsString("bootstrap.min.css")))
                .andExpect(content().string(containsString("bootstrap-icons.min.css")))
                .andExpect(content().string(containsString("/css/app.css")))
                .andExpect(content().string(containsString("bootstrap.bundle.min.js")))
                .andExpect(content().string(containsString("navbar-toggler")))
                .andExpect(content().string(containsString("data-bs-toggle=\"collapse\"")))
                .andExpect(content().string(containsString("data-bs-target=\"#mainNavbar\"")));
    }

    @Test
    @DisplayName("Rooms Catalog page has responsive layout, filter controls, and room grid")
    void roomsCatalog_hasResponsiveGridAndFilterControls() throws Exception {
        mockMvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"")))
                .andExpect(content().string(containsString("col-12 col-sm-6 col-lg-3")))
                .andExpect(content().string(containsString("form-select")))
                .andExpect(content().string(containsString("form-control")));
    }

    @Test
    @DisplayName("Login page has responsive multi-tier column breakpoint card container")
    void loginPage_hasResponsiveCardAndFormClasses() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"")))
                .andExpect(content().string(containsString("col-12 col-sm-10 col-md-8 col-lg-5 col-xl-4")))
                .andExpect(content().string(containsString("form-control")));
    }

    @Test
    @WithMockUser(username = "customer-responsive@example.com", roles = "CUSTOMER")
    @DisplayName("Customer Dashboard renders responsive grid, flex wrapping, and table-responsive container")
    void customerDashboard_hasResponsiveTablesAndCards() throws Exception {
        mockMvc.perform(get("/customer/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"")))
                .andExpect(content().string(containsString("table-responsive")))
                .andExpect(content().string(containsString("d-flex flex-column flex-md-row")))
                .andExpect(content().string(containsString("row g-3")));
    }

    @Test
    @WithMockUser(username = "admin@stayease.com", roles = "ADMIN")
    @DisplayName("Admin Dashboard renders responsive layout, admin navbar toggler, and table-responsive container")
    void adminDashboard_hasResponsiveTableAndKpiGrid() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"")))
                .andExpect(content().string(containsString("navbar-toggler")))
                .andExpect(content().string(containsString("data-bs-target=\"#adminNavbar\"")))
                .andExpect(content().string(containsString("table-responsive")))
                .andExpect(content().string(containsString("col-sm-6 col-lg-3")));
    }

    @Test
    @WithMockUser(username = "admin@stayease.com", roles = "ADMIN")
    @DisplayName("Admin Rooms list renders responsive filter bar and table-responsive wrapper")
    void adminRoomsList_hasResponsiveFilterBarAndTable() throws Exception {
        mockMvc.perform(get("/admin/rooms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"")))
                .andExpect(content().string(containsString("table-responsive")))
                .andExpect(content().string(containsString("form-select")))
                .andExpect(content().string(containsString("col-md-4")));
    }

    @Test
    @WithMockUser(username = "admin@stayease.com", roles = "ADMIN")
    @DisplayName("Admin Bookings list renders responsive filter bar, reset button, and table-responsive wrapper")
    void adminBookingsList_hasResponsiveFilterBarAndReset() throws Exception {
        mockMvc.perform(get("/admin/bookings").param("status", "ALL"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"")))
                .andExpect(content().string(containsString("table-responsive")))
                .andExpect(content().string(containsString("col-lg-5 col-md-4")))
                .andExpect(content().string(containsString("Reset Filters")));
    }
}

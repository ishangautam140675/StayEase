package com.stayease.controller;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminBookingControllerTest {

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

    private User admin;
    private User customer;
    private Room room;
    private Booking pendingBooking;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();

        userRepository.findByEmail("admin.bookings@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("cust.bookings@stayease.com").ifPresent(userRepository::delete);

        admin = userRepository.save(new User(
                "Admin Bookings",
                "admin.bookings@stayease.com",
                passwordEncoder.encode("Password123"),
                null,
                Role.ADMIN
        ));

        customer = userRepository.save(new User(
                "Customer Bookings",
                "cust.bookings@stayease.com",
                passwordEncoder.encode("Password123"),
                "1234567890",
                Role.CUSTOMER
        ));

        room = roomRepository.save(new Room(
                "301",
                RoomType.DOUBLE,
                2,
                new BigDecimal("800.00"),
                "Room 301",
                true
        ));

        pendingBooking = bookingRepository.save(new Booking(
                customer,
                room,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(9),
                1,
                BookingStatus.PENDING,
                null
        ));
    }

    @Test
    @DisplayName("CUSTOMER role gets 403 Forbidden on all /admin/bookings routes")
    void customer_gets403OnAdminBookingRoutes() throws Exception {
        // GET /admin/bookings
        mockMvc.perform(get("/admin/bookings")
                        .with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        // POST /admin/bookings/{id}/approve
        mockMvc.perform(post("/admin/bookings/" + pendingBooking.getId() + "/approve")
                        .with(user(customer.getEmail()).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // POST /admin/bookings/{id}/reject
        mockMvc.perform(post("/admin/bookings/" + pendingBooking.getId() + "/reject")
                        .with(user(customer.getEmail()).roles("CUSTOMER"))
                        .with(csrf())
                        .param("remark", "Maintenance"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Anonymous visitors are redirected to /login on all /admin/bookings routes")
    void anonymous_isRedirectedToLogin() throws Exception {
        // GET /admin/bookings
        mockMvc.perform(get("/admin/bookings"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        // POST /admin/bookings/{id}/approve
        mockMvc.perform(post("/admin/bookings/" + pendingBooking.getId() + "/approve")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        // POST /admin/bookings/{id}/reject
        mockMvc.perform(post("/admin/bookings/" + pendingBooking.getId() + "/reject")
                        .with(csrf())
                        .param("remark", "Maintenance"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("ADMIN can approve a pending booking with CSRF")
    void admin_canApproveBooking_withCsrf() throws Exception {
        mockMvc.perform(post("/admin/bookings/" + pendingBooking.getId() + "/approve")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/bookings"))
                .andExpect(flash().attribute("successMessage", "Booking #" + pendingBooking.getId() + " has been approved."));

        Booking updated = bookingRepository.findById(pendingBooking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.APPROVED);
    }

    @Test
    @DisplayName("ADMIN can reject a pending booking with remark and CSRF")
    void admin_canRejectBooking_withCsrf() throws Exception {
        mockMvc.perform(post("/admin/bookings/" + pendingBooking.getId() + "/reject")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf())
                        .param("remark", "Selected dates undergo annual sanitization"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/bookings"))
                .andExpect(flash().attribute("successMessage", "Booking #" + pendingBooking.getId() + " has been rejected."));

        Booking updated = bookingRepository.findById(pendingBooking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(updated.getAdminRemark()).isEqualTo("Selected dates undergo annual sanitization");
    }

    @Test
    @DisplayName("Attempting to approve or reject an unknown booking ID returns HTTP 404")
    void unknownId_returns404() throws Exception {
        // Approve non-existent ID
        mockMvc.perform(post("/admin/bookings/99999/approve")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        // Reject non-existent ID
        mockMvc.perform(post("/admin/bookings/99999/reject")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf())
                        .param("remark", "Reason"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /admin/bookings displays filter tabs and bookings table for ADMIN")
    void admin_canViewBookingsList() throws Exception {
        mockMvc.perform(get("/admin/bookings")
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/bookings"))
                .andExpect(model().attributeExists("bookings"))
                .andExpect(model().attributeExists("counts"))
                .andExpect(model().attribute("selectedStatus", "PENDING"));
    }

    @Test
    @DisplayName("GET /admin/bookings/export returns CSV with 200, correct headers, and honors status filter")
    void admin_canExportBookingsCsv_andFilterLimitsRows() throws Exception {
        // Create an APPROVED booking as well
        Booking approvedBooking = new Booking(
                customer, room,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(12),
                2,
                BookingStatus.APPROVED,
                null
        );
        bookingRepository.save(approvedBooking);

        String expectedFilename = "bookings-" + LocalDate.now() + ".csv";

        // 1. Export without filter or with ALL -> contains both
        mockMvc.perform(get("/admin/bookings/export")
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=" + expectedFilename))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Booking ID,Customer name")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PENDING")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("APPROVED")));

        // 2. Export with status=PENDING -> limits rows to PENDING only
        mockMvc.perform(get("/admin/bookings/export")
                        .param("status", "PENDING")
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=" + expectedFilename))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PENDING")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("APPROVED"))));

        // 3. Export with status=APPROVED -> limits rows to APPROVED only
        mockMvc.perform(get("/admin/bookings/export")
                        .param("status", "APPROVED")
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("APPROVED")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PENDING"))));
    }

    @Test
    @DisplayName("CUSTOMER gets 403 on /admin/bookings/export")
    void customer_exportBookings_forbidden() throws Exception {
        mockMvc.perform(get("/admin/bookings/export")
                        .with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Anonymous user is redirected to login on /admin/bookings/export")
    void anonymous_exportBookings_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/bookings/export"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}

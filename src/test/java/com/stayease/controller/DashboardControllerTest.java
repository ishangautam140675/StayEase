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

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTest {

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

    private User adminUser;
    private User customerUser;
    private Room roomA;
    private Room roomB;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        roomRepository.flush();
        userRepository.findByEmail("dash.admin@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("dash.cust@stayease.com").ifPresent(userRepository::delete);

        adminUser = userRepository.save(new User(
                "Dash Admin", "dash.admin@stayease.com", passwordEncoder.encode("Pass123"), "1111111111", Role.ADMIN
        ));
        customerUser = userRepository.save(new User(
                "Dash Customer", "dash.cust@stayease.com", passwordEncoder.encode("Pass123"), "2222222222", Role.CUSTOMER
        ));

        roomA = roomRepository.save(new Room("DASH-A", RoomType.SINGLE, 1, BigDecimal.valueOf(500), "Room A", true));
        roomB = roomRepository.save(new Room("DASH-B", RoomType.DOUBLE, 2, BigDecimal.valueOf(900), "Room B", true));
    }

    @Test
    @DisplayName("Admin dashboard renders for ADMIN, contains Occupancy rate and does NOT contain Upcoming bookings")
    void adminDashboard_rendersForAdmin_andDoesNotContainUpcomingBookings() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .with(user(adminUser.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("stats"))
                .andExpect(content().string(containsString("Occupancy Rate")))
                .andExpect(content().string(not(containsStringIgnoringCase("Upcoming bookings"))));
    }

    @Test
    @DisplayName("Customer dashboard renders for CUSTOMER, contains Upcoming Bookings and does NOT contain Occupancy rate")
    void customerDashboard_rendersForCustomer_andDoesNotContainOccupancyRate() throws Exception {
        mockMvc.perform(get("/customer/dashboard")
                        .with(user(customerUser.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/dashboard"))
                .andExpect(model().attributeExists("view"))
                .andExpect(content().string(containsString("Upcoming Bookings")))
                .andExpect(content().string(not(containsStringIgnoringCase("Occupancy rate"))));
    }

    @Test
    @DisplayName("Each dashboard is forbidden for the wrong role")
    void dashboards_roleRestricted() throws Exception {
        // Customer cannot access admin dashboard
        mockMvc.perform(get("/admin/dashboard")
                        .with(user(customerUser.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        // Admin cannot access customer dashboard
        mockMvc.perform(get("/customer/dashboard")
                        .with(user(adminUser.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Availability search returns only free rooms for requested dates")
    void availabilitySearch_returnsOnlyFreeRooms() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = LocalDate.now().plusDays(15);

        // Room A is booked
        bookingRepository.save(new Booking(customerUser, roomA, checkIn, checkOut, 1, BookingStatus.APPROVED, null));

        mockMvc.perform(get("/customer/availability")
                        .with(user(customerUser.getEmail()).roles("CUSTOMER"))
                        .param("checkIn", checkIn.toString())
                        .param("checkOut", checkOut.toString())
                        .param("guests", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/availability"))
                .andExpect(model().attribute("rooms", hasSize(1)))
                .andExpect(content().string(containsString("DASH-B")))
                .andExpect(content().string(not(containsString("DASH-A"))));
    }

    @Test
    @DisplayName("Availability search handles checkout before checkin gracefully with error message")
    void availabilitySearch_handlesCheckoutBeforeCheckIn() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(15);
        LocalDate checkOut = LocalDate.now().plusDays(10); // Checkout before checkin

        mockMvc.perform(get("/customer/availability")
                        .with(user(customerUser.getEmail()).roles("CUSTOMER"))
                        .param("checkIn", checkIn.toString())
                        .param("checkOut", checkOut.toString())
                        .param("guests", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/availability"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(content().string(containsString("Check-out date must be after check-in date")));
    }
}

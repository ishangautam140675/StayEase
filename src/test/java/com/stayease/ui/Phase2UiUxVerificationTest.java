package com.stayease.ui;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class Phase2UiUxVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    private Room singleRoom;
    private Room deluxeRoom;
    private User customer;
    private User admin;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        bookingRepository.flush();
        roomRepository.deleteAll();
        roomRepository.flush();
        userRepository.findByEmail("cust.ui@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("admin.ui@stayease.com").ifPresent(userRepository::delete);
        userRepository.flush();

        customer = userRepository.save(new User(
                "UI Customer", "cust.ui@stayease.com", "$2a$10$7Zz4...dummy", "9876543210", Role.CUSTOMER
        ));

        admin = userRepository.save(new User(
                "UI Admin", "admin.ui@stayease.com", "$2a$10$7Zz4...dummy", "9876543211", Role.ADMIN
        ));

        singleRoom = roomRepository.save(new Room(
                "101", RoomType.SINGLE, 1, new BigDecimal("650.00"), "Executive Single Suite with modern desk and fast WiFi", true
        ));

        deluxeRoom = roomRepository.save(new Room(
                "202", RoomType.DOUBLE, 2, new BigDecimal("1200.00"), "Deluxe Twin Sharing Room with attached balcony", true
        ));

        LocalDate now = LocalDate.now();
        bookingRepository.save(new Booking(customer, singleRoom, now.plusDays(2), now.plusDays(5), 1, BookingStatus.APPROVED, null));
        bookingRepository.save(new Booking(customer, deluxeRoom, now.plusDays(10), now.plusDays(14), 2, BookingStatus.PENDING, null));
    }

    @Test
    @DisplayName("Verify Landing Page (/) contains editorial branding, search controls, and feature blocks")
    void testLandingPageUi() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("StayEase")))
                .andExpect(content().string(containsString("FIND YOUR NEXT STAY")))
                .andExpect(content().string(containsString("Check Availability")))
                .andExpect(content().string(containsString("color-block-oxblood")))
                .andExpect(content().string(containsString("color-block-cobalt")))
                .andExpect(content().string(containsString("color-block-tangerine")))
                .andExpect(content().string(containsString("/rooms")));
    }

    @Test
    @DisplayName("Verify Room Catalog (/rooms) renders card grid, category badges, tariffs, and filters")
    void testRoomCatalogUi() throws Exception {
        mockMvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("101")))
                .andExpect(content().string(containsString("202")))
                .andExpect(content().string(containsString("SINGLE")))
                .andExpect(content().string(containsString("DOUBLE")))
                .andExpect(content().string(containsString("hero_room.jpg")))
                .andExpect(content().string(containsString("deluxe_room.jpg")))
                .andExpect(content().string(containsString("room-card")));
    }

    @Test
    @DisplayName("Verify Room Details (/rooms/{id}) renders photo banner, specifications grid, and sticky tariff card")
    void testRoomDetailsUi() throws Exception {
        mockMvc.perform(get("/rooms/" + singleRoom.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("101")))
                .andExpect(content().string(containsString("SINGLE")))
                .andExpect(content().string(containsString("HOSTEL ACCOMMODATION")))
                .andExpect(content().string(containsString("About This Room")))
                .andExpect(content().string(containsString("Stay Features &amp; Amenities")))
                .andExpect(content().string(containsString("Nightly Tariff")));
    }

    @Test
    @DisplayName("Verify Auth Login (/login) and Register (/register) layouts")
    void testAuthPagesUi() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in")))
                .andExpect(content().string(containsString("name=\"email\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("/register")));

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create your account")))
                .andExpect(content().string(containsString("name=\"fullName\"")))
                .andExpect(content().string(containsString("name=\"email\"")))
                .andExpect(content().string(containsString("name=\"phone\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("/login")));
    }

    @Test
    @WithMockUser(username = "cust.ui@stayease.com", roles = "CUSTOMER")
    @DisplayName("Verify Customer Dashboard (/customer/dashboard) metrics and upcoming bookings table")
    void testCustomerDashboardUi() throws Exception {
        mockMvc.perform(get("/customer/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Guest Portal")))
                .andExpect(content().string(containsString("Upcoming Stays")))
                .andExpect(content().string(containsString("Pending Requests")))
                .andExpect(content().string(containsString("Past Stays")))
                .andExpect(content().string(containsString("Next Stay")))
                .andExpect(content().string(containsString("Quick Availability Search")))
                .andExpect(content().string(containsString("101")));
    }

    @Test
    @WithMockUser(username = "cust.ui@stayease.com", roles = "CUSTOMER")
    @DisplayName("Verify Customer Booking Form (/customer/bookings/new) live tariff estimator box")
    void testCustomerBookingFormUi() throws Exception {
        mockMvc.perform(get("/customer/bookings/new").param("roomId", String.valueOf(singleRoom.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reserve Your Stay")))
                .andExpect(content().string(containsString("101")))
                .andExpect(content().string(containsString("checkIn")))
                .andExpect(content().string(containsString("checkOut")))
                .andExpect(content().string(containsString("priceEstimateBox")))
                .andExpect(content().string(containsString("nightsCountText")));
    }

    @Test
    @WithMockUser(username = "cust.ui@stayease.com", roles = "CUSTOMER")
    @DisplayName("Verify Customer Availability (/customer/availability) room cards with image banners")
    void testCustomerAvailabilityUi() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(20);
        LocalDate checkOut = LocalDate.now().plusDays(25);

        mockMvc.perform(get("/customer/availability")
                        .param("checkIn", checkIn.toString())
                        .param("checkOut", checkOut.toString())
                        .param("guests", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Room Availability")))
                .andExpect(content().string(containsString("Reserve This Room")))
                .andExpect(content().string(containsString("amenity-chip")));
    }

    @Test
    @WithMockUser(username = "admin.ui@stayease.com", roles = "ADMIN")
    @DisplayName("Verify Admin Dashboard (/admin/dashboard) KPIs and pending table")
    void testAdminDashboardUi() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Management Console")))
                .andExpect(content().string(containsString("Total Rooms")))
                .andExpect(content().string(containsString("Available Today")))
                .andExpect(content().string(containsString("Pending Requests")))
                .andExpect(content().string(containsString("Occupancy Rate")))
                .andExpect(content().string(containsString("Bookings per Month")));
    }

    @Test
    @WithMockUser(username = "admin.ui@stayease.com", roles = "ADMIN")
    @DisplayName("Verify Admin Room Management (/admin/rooms and /admin/rooms/new)")
    void testAdminRoomsUi() throws Exception {
        mockMvc.perform(get("/admin/rooms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Manage Rooms")))
                .andExpect(content().string(containsString("101")))
                .andExpect(content().string(containsString("202")))
                .andExpect(content().string(containsString("Active")));

        mockMvc.perform(get("/admin/rooms/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Room Configuration")))
                .andExpect(content().string(containsString("roomNumber")))
                .andExpect(content().string(containsString("capacity")))
                .andExpect(content().string(containsString("pricePerNight")));
    }

    @Test
    @WithMockUser(username = "admin.ui@stayease.com", roles = "ADMIN")
    @DisplayName("Verify Admin Bookings Review (/admin/bookings) status tabs, actions, and remark inputs")
    void testAdminBookingsUi() throws Exception {
        mockMvc.perform(get("/admin/bookings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Manage Bookings")))
                .andExpect(content().string(containsString("Pending")))
                .andExpect(content().string(containsString("Approved")))
                .andExpect(content().string(containsString("Export CSV")))
                .andExpect(content().string(containsString("approve")))
                .andExpect(content().string(containsString("reject")));
    }
}

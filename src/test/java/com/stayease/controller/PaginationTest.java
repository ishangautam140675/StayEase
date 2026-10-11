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
class PaginationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    private User admin;
    private User customer;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.findByEmail("page.admin@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("page.cust@stayease.com").ifPresent(userRepository::delete);

        admin = userRepository.save(new User(
                "Page Admin", "page.admin@stayease.com", "Pass1234", "1111111111", Role.ADMIN
        ));
        customer = userRepository.save(new User(
                "Page Customer", "page.cust@stayease.com", "Pass1234", "2222222222", Role.CUSTOMER
        ));
    }

    @Test
    @DisplayName("With 25 rooms, page 1 shows 9, page 3 shows 7, page past end does not return 500, and filters are kept in page links")
    void roomsPagination_twentyFiveRooms_page1Shows9_page3Shows7_pastEndNot500_filtersRetained() throws Exception {
        // Seed exactly 25 rooms
        for (int i = 1; i <= 25; i++) {
            String roomNum = String.format("R%03d", i);
            RoomType type = (i % 2 == 0) ? RoomType.DOUBLE : RoomType.SINGLE;
            roomRepository.save(new Room(
                    roomNum,
                    type,
                    2,
                    BigDecimal.valueOf(500 + i * 10),
                    "Room " + roomNum,
                    true
            ));
        }

        // Page 1 shows 9 rooms
        mockMvc.perform(get("/rooms").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("rooms", hasSize(9)))
                .andExpect(content().string(containsString("page=2")));

        // Page 3 shows 7 rooms
        mockMvc.perform(get("/rooms").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("rooms", hasSize(7)));

        // A page number past the end does not return 500 and renders empty state
        mockMvc.perform(get("/rooms").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("rooms", empty()))
                .andExpect(content().string(containsString("No rooms match your search")));

        // Negative page number does not crash or return 500; defaults to page 1
        mockMvc.perform(get("/rooms").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("rooms", hasSize(9)));

        // Filters are kept in the page links
        // When filtering by type=DOUBLE, 12 rooms match (9 on page 1, 3 on page 2)
        mockMvc.perform(get("/rooms")
                        .param("type", "DOUBLE")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("rooms", hasSize(9)))
                .andExpect(content().string(containsString("type=DOUBLE")))
                .andExpect(content().string(containsString("page=2")));
    }

    @Test
    @DisplayName("Admin bookings pagination: 15 bookings show 10 on page 1, 5 on page 2, and status filter is kept in links")
    void adminBookingsPagination_fifteenBookings_shows10OnPage1_andStatusKeptInLinks() throws Exception {
        Room r = roomRepository.save(new Room("A101", RoomType.SINGLE, 1, BigDecimal.valueOf(600), "Admin Room", true));

        LocalDate baseDate = LocalDate.now().plusDays(10);
        for (int i = 0; i < 15; i++) {
            bookingRepository.save(new Booking(
                    customer,
                    r,
                    baseDate.plusDays(i * 3),
                    baseDate.plusDays(i * 3 + 2),
                    1,
                    BookingStatus.PENDING,
                    null
            ));
        }

        // Page 1 shows 10 bookings
        mockMvc.perform(get("/admin/bookings")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .param("page", "1")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("bookings", hasSize(10)))
                .andExpect(content().string(containsString("status=PENDING")))
                .andExpect(content().string(containsString("page=2")));

        // Page 2 shows 5 bookings
        mockMvc.perform(get("/admin/bookings")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .param("page", "2")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("bookings", hasSize(5)));

        // Page number past the end does not return 500
        mockMvc.perform(get("/admin/bookings")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .param("page", "88")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("bookings", empty()));
    }

    @Test
    @DisplayName("Customer bookings pagination: 15 bookings show 10 on page 1 and 5 on page 2")
    void customerBookingsPagination_fifteenBookings_shows10OnPage1_and5OnPage2() throws Exception {
        Room r = roomRepository.save(new Room("C101", RoomType.SINGLE, 1, BigDecimal.valueOf(600), "Cust Room", true));

        LocalDate baseDate = LocalDate.now().plusDays(5);
        for (int i = 0; i < 15; i++) {
            bookingRepository.save(new Booking(
                    customer,
                    r,
                    baseDate.plusDays(i * 4),
                    baseDate.plusDays(i * 4 + 2),
                    1,
                    BookingStatus.PENDING,
                    null
            ));
        }

        // Page 1 shows 10 bookings
        mockMvc.perform(get("/customer/bookings")
                        .with(user(customer.getEmail()).roles("CUSTOMER"))
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("bookings", hasSize(10)))
                .andExpect(content().string(containsString("page=2")));

        // Page 2 shows 5 bookings
        mockMvc.perform(get("/customer/bookings")
                        .with(user(customer.getEmail()).roles("CUSTOMER"))
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("bookings", hasSize(5)));

        // Page number past the end does not return 500
        mockMvc.perform(get("/customer/bookings")
                        .with(user(customer.getEmail()).roles("CUSTOMER"))
                        .param("page", "50"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("bookings", empty()));
    }
}

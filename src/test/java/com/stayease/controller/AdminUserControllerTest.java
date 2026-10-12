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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerTest {

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

    @BeforeEach
    void setUp() {
        admin = userRepository.findByEmail("admin@stayease.com").orElseGet(() -> {
            User u = new User();
            u.setFullName("Admin User");
            u.setEmail("admin@stayease.com");
            u.setPasswordHash(passwordEncoder.encode("Admin@123"));
            u.setRole(Role.ADMIN);
            return userRepository.save(u);
        });

        customer = userRepository.findByEmail("testguest@example.com").orElseGet(() -> {
            User u = new User();
            u.setFullName("Test Guest");
            u.setEmail("testguest@example.com");
            u.setPhone("9876543210");
            u.setPasswordHash(passwordEncoder.encode("Guest@123"));
            u.setRole(Role.CUSTOMER);
            return userRepository.save(u);
        });
    }

    @Test
    @DisplayName("Anonymous users cannot access /admin/users")
    void anonymousUserDenied() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Admin can view users list")
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attributeExists("users", "page"));
    }

    @Test
    @DisplayName("Admin can update user full name, email, and phone")
    void adminCanUpdateUser() throws Exception {
        mockMvc.perform(post("/admin/users/" + customer.getId() + "/update")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf())
                        .param("fullName", "Updated Guest Name")
                        .param("email", "updatedguest@example.com")
                        .param("phone", "9123456789")
                        .param("redirectUrl", "/admin/bookings?status=ALL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/bookings?status=ALL"))
                .andExpect(flash().attributeExists("successMessage"));

        User reloaded = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Updated Guest Name");
        assertThat(reloaded.getEmail()).isEqualTo("updatedguest@example.com");
        assertThat(reloaded.getPhone()).isEqualTo("9123456789");
    }

    @Test
    @DisplayName("Admin cannot update user with duplicate existing email")
    void duplicateEmailReturnsError() throws Exception {
        mockMvc.perform(post("/admin/users/" + customer.getId() + "/update")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf())
                        .param("fullName", "Should Fail")
                        .param("email", admin.getEmail())
                        .param("redirectUrl", "/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Admin can remove user and associated bookings")
    void adminCanRemoveUser() throws Exception {
        Room room = roomRepository.findAll().stream().findFirst().orElseGet(() -> {
            Room r = new Room();
            r.setRoomNumber("TEST-999");
            r.setType(RoomType.DOUBLE);
            r.setPricePerNight(BigDecimal.valueOf(2500));
            r.setCapacity(2);
            r.setDescription("Test room");
            return roomRepository.save(r);
        });

        Booking b = new Booking();
        b.setUser(customer);
        b.setRoom(room);
        b.setCheckIn(LocalDate.now().plusDays(2));
        b.setCheckOut(LocalDate.now().plusDays(5));
        b.setGuests(1);
        b.setTotalPrice(BigDecimal.valueOf(7500));
        b.setStatus(BookingStatus.PENDING);
        b.setBookingReference("SE-TEST-DEL");
        bookingRepository.save(b);

        mockMvc.perform(post("/admin/users/" + customer.getId() + "/delete")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf())
                        .param("redirectUrl", "/admin/bookings?status=PENDING"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/bookings?status=PENDING"))
                .andExpect(flash().attributeExists("successMessage"));

        assertThat(userRepository.findById(customer.getId())).isEmpty();
        assertThat(bookingRepository.findByBookingReference("SE-TEST-DEL")).isEmpty();
    }

    @Test
    @DisplayName("Admin cannot delete their own account")
    void adminCannotDeleteSelf() throws Exception {
        mockMvc.perform(post("/admin/users/" + admin.getId() + "/delete")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf())
                        .param("redirectUrl", "/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("errorMessage"));

        assertThat(userRepository.findById(admin.getId())).isPresent();
    }
}

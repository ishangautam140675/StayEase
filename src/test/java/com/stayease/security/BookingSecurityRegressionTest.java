package com.stayease.security;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1 security regression tests.
 *
 * <p>Covers:
 * <ol>
 *   <li>IDOR prevention: a customer cannot cancel another customer's booking via HTTP.</li>
 *   <li>HTTP 404 (not 500) for unknown booking IDs on approve/reject.</li>
 *   <li>Graceful redirect when repeating approve/reject on an already-decided booking.</li>
 *   <li>CSRF enforcement on sensitive admin POST endpoints.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingSecurityRegressionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User ownerCustomer;
    private User attackerCustomer;
    private User admin;
    private Room room;
    private Booking ownerPendingBooking;
    private Booking approvedBooking;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();

        userRepository.findByEmail("owner.sec@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("attacker.sec@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("admin.sec@stayease.com").ifPresent(userRepository::delete);

        ownerCustomer = userRepository.save(new User(
                "Owner Customer", "owner.sec@stayease.com",
                passwordEncoder.encode("Pass1234"), "1111111111", Role.CUSTOMER));

        attackerCustomer = userRepository.save(new User(
                "Attacker Customer", "attacker.sec@stayease.com",
                passwordEncoder.encode("Pass1234"), "2222222222", Role.CUSTOMER));

        admin = userRepository.save(new User(
                "Admin Sec", "admin.sec@stayease.com",
                passwordEncoder.encode("Pass1234"), null, Role.ADMIN));

        room = roomRepository.save(new Room(
                "SEC-101", RoomType.DOUBLE, 2,
                new BigDecimal("800.00"), "Security test room", true));

        ownerPendingBooking = bookingRepository.save(new Booking(
                ownerCustomer, room,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(9),
                1, BookingStatus.PENDING, null));

        approvedBooking = bookingRepository.save(new Booking(
                ownerCustomer, room,
                LocalDate.now().plusDays(15), LocalDate.now().plusDays(18),
                1, BookingStatus.APPROVED, null));
    }

    // ---- 1. IDOR Prevention on Cancel -------------------------------------------

    @Test
    @DisplayName("IDOR: attacker customer cannot cancel another customer's booking")
    void idor_attackerCannotCancelOthersBooking_statusRemainsUnchanged() throws Exception {
        mockMvc.perform(post("/customer/bookings/" + ownerPendingBooking.getId() + "/cancel")
                        .with(user(attackerCustomer.getEmail()).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        Booking reloaded = bookingRepository.findById(ownerPendingBooking.getId()).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("Booking owned by ownerCustomer must not be cancelled by attackerCustomer")
                .isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("IDOR regression guard: booking owner can still cancel their own booking via HTTP")
    void owner_canCancelOwnBooking_viaHttp() throws Exception {
        mockMvc.perform(post("/customer/bookings/" + ownerPendingBooking.getId() + "/cancel")
                        .with(user(ownerCustomer.getEmail()).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings"));

        Booking reloaded = bookingRepository.findById(ownerPendingBooking.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    // ---- 2. 404 for Unknown Booking IDs -----------------------------------------

    @Test
    @DisplayName("POST /admin/bookings/99999/approve returns 404 for a non-existent booking")
    void approveUnknownId_returns404() throws Exception {
        mockMvc.perform(post("/admin/bookings/99999/approve")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /admin/bookings/99999/reject returns 404 for a non-existent booking")
    void rejectUnknownId_returns404() throws Exception {
        mockMvc.perform(post("/admin/bookings/99999/reject")
                        .param("remark", "No such booking")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---- 3. Graceful Handling of Repeated State Transitions ---------------------

    @Test
    @DisplayName("Approving an already-APPROVED booking redirects gracefully, no 500")
    void approveAlreadyApproved_redirectsWithErrorFlash() throws Exception {
        mockMvc.perform(post("/admin/bookings/" + approvedBooking.getId() + "/approve")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/bookings"));
    }

    @Test
    @DisplayName("Rejecting an already-APPROVED booking redirects gracefully, no 500")
    void rejectAlreadyApproved_redirectsWithErrorFlash() throws Exception {
        mockMvc.perform(post("/admin/bookings/" + approvedBooking.getId() + "/reject")
                        .param("remark", "Late change of mind")
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/bookings"));
    }

    // ---- 4. CSRF Enforcement on Sensitive Admin POST Endpoints ------------------

    @Test
    @DisplayName("POST /admin/bookings/{id}/approve without CSRF token returns 403")
    void approveWithoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/admin/bookings/" + ownerPendingBooking.getId() + "/approve")
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/bookings/{id}/reject without CSRF token returns 403")
    void rejectWithoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/admin/bookings/" + ownerPendingBooking.getId() + "/reject")
                        .param("remark", "Testing CSRF")
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
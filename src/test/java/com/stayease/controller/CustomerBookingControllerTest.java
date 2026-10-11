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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerBookingControllerTest {

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

    private User customerA;
    private User customerB;
    private User adminUser;
    private Room roomA;
    private Room roomB;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();

        userRepository.findByEmail("custA@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("custB@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("admin.test@stayease.com").ifPresent(userRepository::delete);

        customerA = userRepository.save(new User(
                "Customer Alpha",
                "custA@stayease.com",
                passwordEncoder.encode("Pass1234"),
                "1111111111",
                Role.CUSTOMER
        ));

        customerB = userRepository.save(new User(
                "Customer Beta",
                "custB@stayease.com",
                passwordEncoder.encode("Pass1234"),
                "2222222222",
                Role.CUSTOMER
        ));

        adminUser = userRepository.save(new User(
                "Admin Tester",
                "admin.test@stayease.com",
                passwordEncoder.encode("Pass1234"),
                null,
                Role.ADMIN
        ));

        roomA = roomRepository.save(new Room(
                "101-A",
                RoomType.SINGLE,
                1,
                new BigDecimal("600.00"),
                "Room A description",
                true
        ));

        roomB = roomRepository.save(new Room(
                "202-B",
                RoomType.DOUBLE,
                2,
                new BigDecimal("900.00"),
                "Room B description",
                true
        ));
    }

    @Test
    @DisplayName("A customer can submit a booking request and is redirected with flash message")
    void customer_canSubmitBooking() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(2);
        LocalDate checkOut = LocalDate.now().plusDays(5);

        mockMvc.perform(post("/customer/bookings/new")
                        .with(user(customerA.getEmail()).roles("CUSTOMER"))
                        .with(csrf())
                        .param("roomId", roomA.getId().toString())
                        .param("checkIn", checkIn.toString())
                        .param("checkOut", checkOut.toString())
                        .param("guests", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings"))
                .andExpect(flash().attribute("successMessage", "Booking request sent. The admin will review it."));

        List<Booking> customerBookings = bookingRepository.findByUserOrderByCreatedAtDesc(customerA);
        assertThat(customerBookings).hasSize(1);
        Booking booking = customerBookings.get(0);
        assertThat(booking.getRoom().getId()).isEqualTo(roomA.getId());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getGuests()).isEqualTo(1);
    }

    @Test
    @DisplayName("Anonymous users are redirected to /login on all customer booking routes")
    void anonymous_isRedirectedToLogin() throws Exception {
        // GET /customer/bookings
        mockMvc.perform(get("/customer/bookings"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        // GET /customer/bookings/new?roomId=...
        mockMvc.perform(get("/customer/bookings/new").param("roomId", roomA.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        // POST /customer/bookings/new
        mockMvc.perform(post("/customer/bookings/new")
                        .with(csrf())
                        .param("roomId", roomA.getId().toString())
                        .param("checkIn", LocalDate.now().plusDays(1).toString())
                        .param("checkOut", LocalDate.now().plusDays(2).toString())
                        .param("guests", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("ADMIN gets 403 Forbidden on all customer booking routes")
    void admin_gets403OnTheseRoutes() throws Exception {
        // GET /customer/bookings
        mockMvc.perform(get("/customer/bookings")
                        .with(user(adminUser.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());

        // GET /customer/bookings/new?roomId=...
        mockMvc.perform(get("/customer/bookings/new")
                        .with(user(adminUser.getEmail()).roles("ADMIN"))
                        .param("roomId", roomA.getId().toString()))
                .andExpect(status().isForbidden());

        // POST /customer/bookings/new
        mockMvc.perform(post("/customer/bookings/new")
                        .with(user(adminUser.getEmail()).roles("ADMIN"))
                        .with(csrf())
                        .param("roomId", roomA.getId().toString())
                        .param("checkIn", LocalDate.now().plusDays(1).toString())
                        .param("checkOut", LocalDate.now().plusDays(2).toString())
                        .param("guests", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Two customers each see only their own bookings in history")
    void twoCustomers_eachSeeOnlyTheirOwnBookings() throws Exception {
        // Booking 1 for Customer A (Room 101-A)
        Booking bookingA = new Booking(customerA, roomA, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), 1, BookingStatus.PENDING, null);
        bookingRepository.save(bookingA);

        // Booking 2 for Customer B (Room 202-B)
        Booking bookingB = new Booking(customerB, roomB, LocalDate.now().plusDays(4), LocalDate.now().plusDays(7), 2, BookingStatus.PENDING, null);
        bookingRepository.save(bookingB);

        // Customer A views history
        mockMvc.perform(get("/customer/bookings")
                        .with(user(customerA.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("bookings"))
                .andExpect(content().string(containsString("101-A")))
                .andExpect(content().string(not(containsString("202-B"))));

        // Customer B views history
        mockMvc.perform(get("/customer/bookings")
                        .with(user(customerB.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("bookings"))
                .andExpect(content().string(containsString("202-B")))
                .andExpect(content().string(not(containsString("101-A"))));
    }

    @Test
    @DisplayName("A tampered userId parameter is ignored and booking is attributed to authenticated customer")
    void tamperedUserIdParameter_isIgnored() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(3);
        LocalDate checkOut = LocalDate.now().plusDays(6);

        // Malicious request passes customerB's id in the parameter payload
        mockMvc.perform(post("/customer/bookings/new")
                        .with(user(customerA.getEmail()).roles("CUSTOMER"))
                        .with(csrf())
                        .param("roomId", roomA.getId().toString())
                        .param("checkIn", checkIn.toString())
                        .param("checkOut", checkOut.toString())
                        .param("guests", "1")
                        .param("userId", customerB.getId().toString())
                        .param("user.id", customerB.getId().toString())
                        .param("userEmail", customerB.getEmail()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings"));

        // Verify Customer B has NO bookings
        List<Booking> bBookings = bookingRepository.findByUserOrderByCreatedAtDesc(customerB);
        assertThat(bBookings).isEmpty();

        // Verify Customer A owns the newly created booking
        List<Booking> aBookings = bookingRepository.findByUserOrderByCreatedAtDesc(customerA);
        assertThat(aBookings).hasSize(1);
        assertThat(aBookings.get(0).getUser().getId()).isEqualTo(customerA.getId());
    }

    @Test
    @DisplayName("Submitting invalid booking re-renders the form with field error messages")
    void submitInvalidBooking_reRendersFormWithErrors() throws Exception {
        // checkOut before checkIn
        mockMvc.perform(post("/customer/bookings/new")
                        .with(user(customerA.getEmail()).roles("CUSTOMER"))
                        .with(csrf())
                        .param("roomId", roomA.getId().toString())
                        .param("checkIn", LocalDate.now().plusDays(3).toString())
                        .param("checkOut", LocalDate.now().plusDays(2).toString())
                        .param("guests", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/booking-form"))
                .andExpect(model().attributeExists("room"))
                .andExpect(model().attributeHasFieldErrors("bookingForm", "checkOut"));
    }

    @Test
    @DisplayName("Submitting a conflicting booking re-renders the form with conflict message and alternatives")
    void submitConflictingBooking_showsConflictMessageAndAlternatives() throws Exception {
        LocalDate existingCheckIn = LocalDate.now().plusDays(10);
        LocalDate existingCheckOut = LocalDate.now().plusDays(15);
        bookingRepository.save(new Booking(
                customerB,
                roomA,
                existingCheckIn,
                existingCheckOut,
                1,
                BookingStatus.APPROVED,
                null
        ));

        // Customer A tries to book room A for overlapping dates
        mockMvc.perform(post("/customer/bookings/new")
                        .with(user(customerA.getEmail()).roles("CUSTOMER"))
                        .with(csrf())
                        .param("roomId", roomA.getId().toString())
                        .param("checkIn", existingCheckIn.plusDays(1).toString())
                        .param("checkOut", existingCheckOut.plusDays(2).toString())
                        .param("guests", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/booking-form"))
                .andExpect(model().attributeExists("conflictMessages"))
                .andExpect(model().attributeExists("alternativeRooms"))
                .andExpect(content().string(containsString("is already booked")))
                .andExpect(content().string(containsString("Book this room instead")))
                .andExpect(content().string(containsString("202-B")));
    }

    // ── Phase 12 Cancellation Tests ─────────────────────────────────────────

    @Test
    @DisplayName("Owner can cancel a PENDING booking via POST with CSRF and gets flash message")
    void cancelBooking_owner_pending_redirectsWithSuccessFlash() throws Exception {
        Booking booking = bookingRepository.save(new Booking(
                customerA, roomA, LocalDate.now().plusDays(5), LocalDate.now().plusDays(8), 1, BookingStatus.PENDING, null
        ));

        mockMvc.perform(post("/customer/bookings/{id}/cancel", booking.getId())
                        .with(user(customerA.getEmail()).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings"))
                .andExpect(flash().attribute("successMessage", "Booking cancelled."));

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("Owner can cancel an APPROVED booking via POST with CSRF and gets flash message")
    void cancelBooking_owner_approved_redirectsWithSuccessFlash() throws Exception {
        Booking booking = bookingRepository.save(new Booking(
                customerA, roomA, LocalDate.now().plusDays(5), LocalDate.now().plusDays(8), 1, BookingStatus.APPROVED, null
        ));

        mockMvc.perform(post("/customer/bookings/{id}/cancel", booking.getId())
                        .with(user(customerA.getEmail()).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings"))
                .andExpect(flash().attribute("successMessage", "Booking cancelled."));

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("Another customer trying to cancel gets 403 and status remains untouched")
    void cancelBooking_otherCustomer_returnsForbidden403() throws Exception {
        Booking booking = bookingRepository.save(new Booking(
                customerA, roomA, LocalDate.now().plusDays(5), LocalDate.now().plusDays(8), 1, BookingStatus.PENDING, null
        ));

        mockMvc.perform(post("/customer/bookings/{id}/cancel", booking.getId())
                        .with(user(customerB.getEmail()).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("ADMIN gets 403 on the cancel URL")
    void cancelBooking_admin_returnsForbidden403() throws Exception {
        Booking booking = bookingRepository.save(new Booking(
                customerA, roomA, LocalDate.now().plusDays(5), LocalDate.now().plusDays(8), 1, BookingStatus.PENDING, null
        ));

        mockMvc.perform(post("/customer/bookings/{id}/cancel", booking.getId())
                        .with(user(adminUser.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /customer/bookings/{id}/cancel without CSRF is rejected with 403")
    void cancelBooking_withoutCsrf_returnsForbidden403() throws Exception {
        Booking booking = bookingRepository.save(new Booking(
                customerA, roomA, LocalDate.now().plusDays(5), LocalDate.now().plusDays(8), 1, BookingStatus.PENDING, null
        ));

        mockMvc.perform(post("/customer/bookings/{id}/cancel", booking.getId())
                        .with(user(customerA.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cancelling REJECTED or CANCELLED booking redirects with error flash message")
    void cancelBooking_invalidState_redirectsWithErrorMessage() throws Exception {
        Booking rejected = bookingRepository.save(new Booking(
                customerA, roomA, LocalDate.now().plusDays(5), LocalDate.now().plusDays(8), 1, BookingStatus.REJECTED, "Denied"
        ));

        mockMvc.perform(post("/customer/bookings/{id}/cancel", rejected.getId())
                        .with(user(customerA.getEmail()).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings"))
                .andExpect(flash().attributeExists("errorMessage"));

        Booking reloaded = bookingRepository.findById(rejected.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.REJECTED);
    }

    @Test
    @DisplayName("Cancelling on or after check-in date redirects with error flash message")
    void cancelBooking_onOrAfterCheckIn_redirectsWithErrorMessage() throws Exception {
        Booking todayBooking = bookingRepository.save(new Booking(
                customerA, roomA, LocalDate.now(), LocalDate.now().plusDays(2), 1, BookingStatus.PENDING, null
        ));

        mockMvc.perform(post("/customer/bookings/{id}/cancel", todayBooking.getId())
                        .with(user(customerA.getEmail()).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings"))
                .andExpect(flash().attributeExists("errorMessage"));

        Booking reloaded = bookingRepository.findById(todayBooking.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("Cancelling an unknown booking ID returns 404")
    void cancelBooking_unknownId_returns404() throws Exception {
        mockMvc.perform(post("/customer/bookings/999999/cancel")
                        .with(user(customerA.getEmail()).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Customer can view their own booking receipt")
    void viewReceipt_asOwner_returnsReceiptPage() throws Exception {
        Booking booking = bookingRepository.save(new Booking(
                customerA, roomA, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), 2, BookingStatus.PENDING, "Special request"
        ));
        booking.setBookingReference("SE-20261011-TEST01");
        booking.setPricePerNightAtBooking(new BigDecimal("120.00"));
        booking.setTotalPrice(new BigDecimal("240.00"));
        bookingRepository.save(booking);

        mockMvc.perform(get("/customer/bookings/{id}/receipt", booking.getId())
                        .with(user(customerA.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/booking-receipt"))
                .andExpect(model().attributeExists("booking", "priceBreakdown", "cancellationLeadHours"))
                .andExpect(content().string(containsString("SE-20261011-TEST01")))
                .andExpect(content().string(containsString("Booking Confirmation")))
                .andExpect(content().string(containsString("Official Receipt")));
    }

    @Test
    @DisplayName("Viewing receipt of another customer's booking returns 403 Forbidden")
    void viewReceipt_asOtherCustomer_returns403() throws Exception {
        Booking booking = bookingRepository.save(new Booking(
                customerA, roomA, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), 2, BookingStatus.PENDING, null
        ));

        mockMvc.perform(get("/customer/bookings/{id}/receipt", booking.getId())
                        .with(user(customerB.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Customer can view receipt via booking reference")
    void viewReceipt_byReference_returnsReceiptPage() throws Exception {
        Booking booking = bookingRepository.save(new Booking(
                customerA, roomA, LocalDate.now().plusDays(5), LocalDate.now().plusDays(7), 2, BookingStatus.PENDING, null
        ));
        booking.setBookingReference("SE-REF-XYZ789");
        bookingRepository.save(booking);

        mockMvc.perform(get("/customer/bookings/reference/{ref}/receipt", "SE-REF-XYZ789")
                        .with(user(customerA.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/booking-receipt"))
                .andExpect(content().string(containsString("SE-REF-XYZ789")));
    }
}


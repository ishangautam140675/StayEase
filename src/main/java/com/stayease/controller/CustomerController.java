package com.stayease.controller;

import com.stayease.dto.BookingForm;
import com.stayease.dto.CustomerDashboardView;
import com.stayease.exception.BookingValidationException;
import com.stayease.model.Booking;
import com.stayease.model.Room;
import com.stayease.model.User;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import com.stayease.service.BookingService;
import com.stayease.service.DashboardService;
import com.stayease.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import com.stayease.exception.InvalidBookingStateException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

/**
 * Controller for authenticated customer interactions: dashboard, booking requests, and history (Phase 13).
 */
@Controller
public class CustomerController {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final BookingService bookingService;
    private final DashboardService dashboardService;

    public CustomerController(UserRepository userRepository,
                              RoomRepository roomRepository,
                              RoomService roomService,
                              BookingService bookingService,
                              DashboardService dashboardService) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.roomService = roomService;
        this.bookingService = bookingService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/customer/dashboard")
    public String dashboard(Principal principal, Model model) {
        String email = principal != null ? principal.getName() : "";
        String userName = "Customer";
        if (principal != null) {
            userName = userRepository.findByEmail(principal.getName())
                    .map(User::getFullName)
                    .orElse(principal.getName());
        }
        CustomerDashboardView dashboardView = dashboardService.customerView(email);

        model.addAttribute("userName", userName);
        model.addAttribute("view", dashboardView);
        model.addAttribute("today", LocalDate.now());
        return "customer/dashboard";
    }

    /**
     * Quick room availability search for customers (Phase 13).
     */
    @GetMapping("/customer/availability")
    public String checkAvailability(
            @RequestParam(value = "checkIn", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(value = "checkOut", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(value = "guests", required = false) Integer guests,
            Model model) {

        int numGuests = (guests != null && guests > 0) ? guests : 1;
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("guests", numGuests);
        model.addAttribute("today", LocalDate.now());

        if (checkIn == null || checkOut == null) {
            model.addAttribute("errorMessage", "Please provide both check-in and check-out dates.");
            model.addAttribute("rooms", List.of());
            return "customer/availability";
        }

        if (checkIn.isBefore(LocalDate.now())) {
            model.addAttribute("errorMessage", "Check-in date cannot be in the past.");
            model.addAttribute("rooms", List.of());
            return "customer/availability";
        }

        if (!checkOut.isAfter(checkIn)) {
            model.addAttribute("errorMessage", "Check-out date must be after check-in date.");
            model.addAttribute("rooms", List.of());
            return "customer/availability";
        }

        List<Room> availableRooms = roomService.findAvailableRooms(checkIn, checkOut, numGuests);
        model.addAttribute("rooms", availableRooms);
        return "customer/availability";
    }

    /**
     * Displays the new booking request form with the selected room summary if provided.
     * Optionally pre-fills requested dates and guests (used when booking alternatives).
     */
    @GetMapping("/customer/bookings/new")
    public String showBookingForm(
            @RequestParam(value = "roomId", required = false) Long roomId,
            @RequestParam(value = "checkIn", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(value = "checkOut", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(value = "guests", required = false) Integer guests,
            Model model) {
        Room room = null;
        if (roomId != null) {
            room = roomService.getActiveRoom(roomId);
        }

        BookingForm bookingForm = new BookingForm();
        if (room != null) {
            bookingForm.setRoomId(room.getId());
        }
        bookingForm.setGuests(guests != null ? guests : 1);
        bookingForm.setCheckIn(checkIn != null ? checkIn : LocalDate.now());
        bookingForm.setCheckOut(checkOut != null ? checkOut : LocalDate.now().plusDays(1));

        model.addAttribute("room", room);
        model.addAttribute("bookingForm", bookingForm);
        model.addAttribute("today", LocalDate.now());
        return "customer/booking-form";
    }

    /**
     * Processes customer booking reservation request.
     * The customer identity is resolved strictly from the logged-in session (Principal.getName()).
     */
    @PostMapping("/customer/bookings/new")
    public String submitBooking(@Valid @ModelAttribute("bookingForm") BookingForm bookingForm,
                                BindingResult bindingResult,
                                Principal principal,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            loadRoomForForm(bookingForm.getRoomId(), model);
            model.addAttribute("today", LocalDate.now());
            return "customer/booking-form";
        }

        try {
            bookingService.createBooking(principal.getName(), bookingForm);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Booking request sent. The admin will review it.");
            return "redirect:/customer/bookings";
        } catch (com.stayease.exception.BookingConflictException ex) {
            Room room = bookingForm.getRoomId() != null
                    ? roomRepository.findById(bookingForm.getRoomId()).orElse(null)
                    : null;
            String roomNumber = room != null ? room.getRoomNumber() : "";

            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH);
            List<String> conflictMessages = ex.getConflictingBookings().stream()
                    .map(b -> {
                        String rNum = b.getRoom() != null ? b.getRoom().getRoomNumber() : roomNumber;
                        return String.format("Room %s is already booked from %s to %s",
                                rNum,
                                b.getCheckIn().format(dtf),
                                b.getCheckOut().format(dtf));
                    })
                    .toList();

            List<Room> alternativeRooms = java.util.Collections.emptyList();
            if (bookingForm.getCheckIn() != null && bookingForm.getCheckOut() != null && bookingForm.getGuests() != null) {
                alternativeRooms = new java.util.ArrayList<>(
                        roomService.findAvailableRooms(bookingForm.getCheckIn(), bookingForm.getCheckOut(), bookingForm.getGuests())
                );
                if (bookingForm.getRoomId() != null) {
                    alternativeRooms.removeIf(r -> r.getId().equals(bookingForm.getRoomId()));
                }
            }

            model.addAttribute("conflictMessages", conflictMessages);
            model.addAttribute("alternativeRooms", alternativeRooms);
            loadRoomForForm(bookingForm.getRoomId(), model);
            model.addAttribute("today", LocalDate.now());
            return "customer/booking-form";
        } catch (BookingValidationException ex) {
            bindingResult.rejectValue(ex.getField(), "validation.booking", ex.getMessage());
            loadRoomForForm(bookingForm.getRoomId(), model);
            model.addAttribute("today", LocalDate.now());
            return "customer/booking-form";
        }
    }

    /**
     * Displays the customer's personal booking reservation history (10 per page).
     */
    @GetMapping("/customer/bookings")
    public String bookingHistory(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                 Principal principal,
                                 Model model) {
        int pageNum = (page != null && page > 0) ? page : 1;
        int pageIndex = pageNum - 1;
        Pageable pageable = PageRequest.of(pageIndex, 10);
        Page<Booking> bookingPage = bookingService.getBookingsForUser(principal.getName(), pageable);

        model.addAttribute("bookings", bookingPage.getContent());
        model.addAttribute("page", bookingPage);
        return "customer/bookings";
    }

    /**
     * Processes customer booking cancellation request (Phase 12).
     */
    @PostMapping("/customer/bookings/{id}/cancel")
    public String cancelBooking(@PathVariable("id") Long id,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        try {
            bookingService.cancel(id, principal.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Booking cancelled.");
        } catch (InvalidBookingStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/customer/bookings";
    }

    private void loadRoomForForm(Long roomId, Model model) {
        if (roomId != null) {
            roomRepository.findById(roomId).ifPresent(r -> model.addAttribute("room", r));
        }
    }
}

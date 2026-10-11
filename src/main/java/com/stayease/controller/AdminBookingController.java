package com.stayease.controller;

import com.stayease.exception.BookingConflictException;
import com.stayease.exception.InvalidBookingStateException;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.service.BookingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Controller for administrative booking reviews, approvals, and rejections.
 */
@Controller
@RequestMapping("/admin/bookings")
public class AdminBookingController {

    private final BookingService bookingService;

    public AdminBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Lists bookings filtered by status (defaults to PENDING, 10 per page).
     */
    @GetMapping
    public String listBookings(@RequestParam(value = "status", required = false, defaultValue = "PENDING") String statusParam,
                               @RequestParam(value = "page", defaultValue = "1") Integer page,
                               Model model) {
        BookingStatus status = null;
        String selectedStatus = statusParam != null ? statusParam.toUpperCase() : "PENDING";

        if (!"ALL".equalsIgnoreCase(selectedStatus)) {
            try {
                status = BookingStatus.valueOf(selectedStatus);
            } catch (IllegalArgumentException e) {
                status = BookingStatus.PENDING;
                selectedStatus = "PENDING";
            }
        }

        int pageNum = (page != null && page > 0) ? page : 1;
        int pageIndex = pageNum - 1;
        Pageable pageable = PageRequest.of(pageIndex, 10);
        Page<Booking> bookingPage = bookingService.listForAdmin(status, pageable);
        Map<String, Long> counts = bookingService.countByStatus();

        model.addAttribute("bookings", bookingPage.getContent());
        model.addAttribute("page", bookingPage);
        model.addAttribute("counts", counts);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("status", selectedStatus);
        return "admin/bookings";
    }

    /**
     * Approves a PENDING booking.
     */
    @PostMapping("/{id}/approve")
    public String approveBooking(@PathVariable("id") Long id,
                                 RedirectAttributes redirectAttributes) {
        try {
            bookingService.approve(id);
            redirectAttributes.addFlashAttribute("successMessage", "Booking #" + id + " has been approved.");
        } catch (InvalidBookingStateException | BookingConflictException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings";
    }

    /**
     * Rejects a PENDING booking with a mandatory remark.
     */
    @PostMapping("/{id}/reject")
    public String rejectBooking(@PathVariable("id") Long id,
                                @RequestParam(value = "remark", required = false) String remark,
                                RedirectAttributes redirectAttributes) {
        if (remark == null || remark.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "A remark is required when rejecting a booking.");
            return "redirect:/admin/bookings";
        }

        try {
            bookingService.reject(id, remark);
            redirectAttributes.addFlashAttribute("successMessage", "Booking #" + id + " has been rejected.");
        } catch (InvalidBookingStateException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings";
    }
}

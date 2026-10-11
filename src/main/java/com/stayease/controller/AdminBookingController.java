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

import com.stayease.service.BookingExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Controller for administrative booking reviews, approvals, and rejections.
 */
@Controller
@RequestMapping("/admin/bookings")
public class AdminBookingController {

    private final BookingService bookingService;
    private final BookingExportService bookingExportService;
    private final com.stayease.service.PaymentService paymentService;

    public AdminBookingController(BookingService bookingService,
                                  BookingExportService bookingExportService,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false) com.stayease.service.PaymentService paymentService) {
        this.bookingService = bookingService;
        this.bookingExportService = bookingExportService;
        this.paymentService = paymentService;
    }

    /**
     * Exports bookings as CSV honoring the status filter.
     */
    /**
     * Exports bookings as CSV honoring status and optional date filters.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportBookings(@RequestParam(value = "status", required = false) String statusParam,
                                                 @RequestParam(value = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                 @RequestParam(value = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        BookingStatus status = null;
        if (statusParam != null && !statusParam.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusParam)) {
            try {
                status = BookingStatus.valueOf(statusParam.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid status and leave status as null (falls back to all)
            }
        }

        List<Booking> bookings = bookingService.listForExport(status, startDate, endDate);
        String csvContent = bookingExportService.toCsv(bookings);
        byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);

        String filename = "bookings-" + LocalDate.now() + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }

    /**
     * Lists bookings filtered by status, search keyword, date ranges, with pagination (10 per page).
     */
    @GetMapping
    public String listBookings(@RequestParam(value = "status", required = false, defaultValue = "PENDING") String statusParam,
                               @RequestParam(value = "query", required = false) String query,
                               @RequestParam(value = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
                               @RequestParam(value = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
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
        Page<Booking> bookingPage = bookingService.searchAdminBookings(status, query, startDate, endDate, pageable);
        Map<String, Long> counts = bookingService.countByStatus();

        Map<Long, List<com.stayease.model.PaymentTransaction>> transactionsByBookingId = new java.util.HashMap<>();
        if (paymentService != null) {
            for (Booking b : bookingPage.getContent()) {
                transactionsByBookingId.put(b.getId(), paymentService.getTransactionsForBooking(b.getId()));
            }
        }

        model.addAttribute("bookings", bookingPage.getContent());
        model.addAttribute("transactionsByBookingId", transactionsByBookingId);
        model.addAttribute("page", bookingPage);
        model.addAttribute("counts", counts);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("status", selectedStatus);
        model.addAttribute("query", query != null ? query.trim() : "");
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "admin/bookings";
    }

    /**
     * Approves a PENDING booking.
     */
    @PostMapping("/{id}/approve")
    public String approveBooking(@PathVariable("id") Long id,
                                 java.security.Principal principal,
                                 RedirectAttributes redirectAttributes) {
        try {
            String adminEmail = principal != null ? principal.getName() : "ADMIN";
            bookingService.approve(id, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Booking #" + id + " has been approved.");
        } catch (InvalidBookingStateException | BookingConflictException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings";
    }

    /**
     * Checks in a guest for an APPROVED booking.
     */
    @PostMapping("/{id}/check-in")
    public String checkInBooking(@PathVariable("id") Long id,
                                 java.security.Principal principal,
                                 RedirectAttributes redirectAttributes) {
        try {
            String adminEmail = principal != null ? principal.getName() : "ADMIN";
            bookingService.checkIn(id, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Guest for booking #" + id + " checked in successfully.");
        } catch (InvalidBookingStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings";
    }

    /**
     * Checks out a guest for a CHECKED_IN booking.
     */
    @PostMapping("/{id}/check-out")
    public String checkOutBooking(@PathVariable("id") Long id,
                                  java.security.Principal principal,
                                  RedirectAttributes redirectAttributes) {
        try {
            String adminEmail = principal != null ? principal.getName() : "ADMIN";
            bookingService.checkOut(id, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Guest for booking #" + id + " checked out successfully.");
        } catch (InvalidBookingStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings";
    }

    /**
     * Marks an APPROVED booking as NO_SHOW.
     */
    @PostMapping("/{id}/no-show")
    public String markNoShowBooking(@PathVariable("id") Long id,
                                    @RequestParam(value = "remark", required = false) String remark,
                                    java.security.Principal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            String adminEmail = principal != null ? principal.getName() : "ADMIN";
            bookingService.markNoShow(id, adminEmail, remark);
            redirectAttributes.addFlashAttribute("successMessage", "Booking #" + id + " marked as No-Show.");
        } catch (InvalidBookingStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings";
    }

    /**
     * Updates operational notes for a booking.
     */
    @PostMapping("/{id}/notes")
    public String updateOperationalNotes(@PathVariable("id") Long id,
                                         @RequestParam(value = "notes", required = false) String notes,
                                         java.security.Principal principal,
                                         RedirectAttributes redirectAttributes) {
        try {
            String adminEmail = principal != null ? principal.getName() : "ADMIN";
            bookingService.updateOperationalNotes(id, notes, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Operational notes updated for booking #" + id + ".");
        } catch (Exception ex) {
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
                                java.security.Principal principal,
                                RedirectAttributes redirectAttributes) {
        if (remark == null || remark.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "A remark is required when rejecting a booking.");
            return "redirect:/admin/bookings";
        }

        try {
            String adminEmail = principal != null ? principal.getName() : "ADMIN";
            bookingService.reject(id, remark, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Booking #" + id + " has been rejected.");
        } catch (InvalidBookingStateException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/bookings";
    }
}

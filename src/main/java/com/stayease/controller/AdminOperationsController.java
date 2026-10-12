package com.stayease.controller;

import com.stayease.exception.BookingConflictException;
import com.stayease.model.RoomType;
import com.stayease.service.AuditLogService;
import com.stayease.service.MaintenanceScheduleService;
import com.stayease.service.RoomService;
import com.stayease.service.SeasonalPricingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Controller managing room maintenance schedules, seasonal pricing rules,
 * and viewing administrative audit logs.
 */
@Controller
@RequestMapping("/admin/operations")
public class AdminOperationsController {

    private final MaintenanceScheduleService maintenanceService;
    private final SeasonalPricingService seasonalPricingService;
    private final AuditLogService auditLogService;
    private final RoomService roomService;
    private final com.stayease.service.BookingExportService exportService;

    public AdminOperationsController(MaintenanceScheduleService maintenanceService,
                                     SeasonalPricingService seasonalPricingService,
                                     AuditLogService auditLogService,
                                     RoomService roomService,
                                     com.stayease.service.BookingExportService exportService) {
        this.maintenanceService = maintenanceService;
        this.seasonalPricingService = seasonalPricingService;
        this.auditLogService = auditLogService;
        this.roomService = roomService;
        this.exportService = exportService;
    }

    // ── Maintenance Scheduling ─────────────────────────────────────────────

    @GetMapping("/maintenance")
    public String listMaintenance(@RequestParam(value = "page", defaultValue = "1") Integer page, Model model) {
        int pageNum = (page != null && page > 0) ? page : 1;
        Pageable pageable = PageRequest.of(pageNum - 1, 10);
        model.addAttribute("schedules", maintenanceService.getAllSchedules(pageable));
        model.addAttribute("rooms", roomService.findAllForAdmin());
        return "admin/maintenance";
    }

    @PostMapping("/maintenance/schedule")
    public String scheduleMaintenance(@RequestParam("roomId") Long roomId,
                                      @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                      @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                      @RequestParam("reason") String reason,
                                      Principal principal,
                                      RedirectAttributes redirectAttributes) {
        try {
            String adminEmail = principal != null ? principal.getName() : "ADMIN";
            maintenanceService.scheduleMaintenance(roomId, startDate, endDate, reason, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Room maintenance successfully scheduled.");
        } catch (BookingConflictException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/operations/maintenance";
    }

    @PostMapping("/maintenance/{id}/delete")
    public String deleteMaintenance(@PathVariable("id") Long id,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            String adminEmail = principal != null ? principal.getName() : "ADMIN";
            maintenanceService.deleteMaintenanceSchedule(id, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Maintenance schedule removed.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/operations/maintenance";
    }

    // ── Seasonal Pricing ───────────────────────────────────────────────────

    @GetMapping("/pricing")
    public String listPricingRules(@RequestParam(value = "page", defaultValue = "1") Integer page, Model model) {
        int pageNum = (page != null && page > 0) ? page : 1;
        Pageable pageable = PageRequest.of(pageNum - 1, 10);
        model.addAttribute("rules", seasonalPricingService.getRules(pageable));
        model.addAttribute("rooms", roomService.findAllForAdmin());
        model.addAttribute("roomTypes", RoomType.values());
        return "admin/pricing";
    }

    @PostMapping("/pricing/create")
    public String createPricingRule(@RequestParam("ruleName") String ruleName,
                                    @RequestParam(value = "roomId", required = false) Long roomId,
                                    @RequestParam(value = "roomType", required = false) RoomType roomType,
                                    @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                    @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                    @RequestParam(value = "pricePerNight", required = false) BigDecimal pricePerNight,
                                    @RequestParam(value = "multiplier", required = false) BigDecimal multiplier,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            String adminEmail = principal != null ? principal.getName() : "ADMIN";
            seasonalPricingService.createRule(ruleName, roomId, roomType, startDate, endDate, pricePerNight, multiplier, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Seasonal pricing rule created successfully.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/operations/pricing";
    }

    @PostMapping("/pricing/{id}/delete")
    public String deletePricingRule(@PathVariable("id") Long id,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            String adminEmail = principal != null ? principal.getName() : "ADMIN";
            seasonalPricingService.deleteRule(id, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Seasonal pricing rule removed.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/operations/pricing";
    }

    // ── Audit Logs ─────────────────────────────────────────────────────────

    @GetMapping("/audit-logs")
    public String listAuditLogs(@RequestParam(value = "adminEmail", required = false) String adminEmail,
                                @RequestParam(value = "action", required = false) String action,
                                @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                @RequestParam(value = "page", defaultValue = "1") Integer page,
                                Model model) {
        int pageNum = (page != null && page > 0) ? page : 1;
        Pageable pageable = PageRequest.of(pageNum - 1, 15);
        model.addAttribute("logs", auditLogService.searchAuditLogs(adminEmail, action, start, end, pageable));
        model.addAttribute("filterEmail", adminEmail != null ? adminEmail.trim() : "");
        model.addAttribute("filterAction", action != null ? action.trim() : "ALL");
        return "admin/audit-logs";
    }

    @GetMapping("/audit-logs/export/csv")
    public org.springframework.http.ResponseEntity<byte[]> exportAuditLogsCsv(
            @RequestParam(value = "adminEmail", required = false) String adminEmail,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        java.util.List<com.stayease.model.AdminAuditLog> logs = auditLogService.searchAuditLogsForExport(adminEmail, action, start, end);
        String csv = exportService.auditLogsToCsv(logs);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String filename = "stayease-audit-logs-" + LocalDate.now() + ".csv";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    /**
     * Deletes an individual audit log record.
     */
    @PostMapping("/audit-logs/{id}/delete")
    public String deleteAuditLog(@PathVariable("id") Long id,
                                 @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
                                 RedirectAttributes redirectAttributes) {
        try {
            auditLogService.deleteLog(id);
            redirectAttributes.addFlashAttribute("successMessage", "Audit log record #" + id + " was deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete audit log: " + e.getMessage());
        }
        if (redirectUrl != null && !redirectUrl.isBlank() && redirectUrl.startsWith("/")) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/admin/operations/audit-logs";
    }

    /**
     * Bulk deletes selected audit log records.
     */
    @PostMapping("/audit-logs/bulk-delete")
    public String bulkDeleteAuditLogs(@RequestParam(value = "logIds", required = false) java.util.List<Long> logIds,
                                      @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
                                      RedirectAttributes redirectAttributes) {
        if (logIds == null || logIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No audit log records selected for deletion.");
        } else {
            try {
                auditLogService.deleteLogs(logIds);
                redirectAttributes.addFlashAttribute("successMessage", logIds.size() + " audit log record(s) deleted successfully.");
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete audit logs: " + e.getMessage());
            }
        }
        if (redirectUrl != null && !redirectUrl.isBlank() && redirectUrl.startsWith("/")) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/admin/operations/audit-logs";
    }

    /**
     * Purges all audit log records.
     */
    @PostMapping("/audit-logs/clear-all")
    public String clearAllAuditLogs(RedirectAttributes redirectAttributes) {
        try {
            auditLogService.clearAllLogs();
            redirectAttributes.addFlashAttribute("successMessage", "All audit log records have been cleared.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to clear audit logs: " + e.getMessage());
        }
        return "redirect:/admin/operations/audit-logs";
    }
}

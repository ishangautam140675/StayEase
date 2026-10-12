package com.stayease.controller;

import com.stayease.dto.RoomForm;
import com.stayease.exception.DuplicateRoomNumberException;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.service.AuditLogService;
import com.stayease.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

/**
 * Administrative controller for room management (CRUD) and filtering.
 */
@Controller
@RequestMapping("/admin/rooms")
public class AdminRoomController {

    private final RoomService roomService;
    private final AuditLogService auditLogService;

    public AdminRoomController(RoomService roomService,
                               @Autowired(required = false) AuditLogService auditLogService) {
        this.roomService = roomService;
        this.auditLogService = auditLogService;
    }

    /**
     * GET /admin/rooms — lists rooms with keyword search, room type filter, status filter, and pagination.
     */
    @GetMapping
    public String listRooms(@RequestParam(value = "query", required = false) String query,
                            @RequestParam(value = "type", required = false) String typeStr,
                            @RequestParam(value = "status", required = false, defaultValue = "ALL") String statusStr,
                            @RequestParam(value = "page", defaultValue = "1") Integer page,
                            Model model) {
        RoomType type = null;
        if (typeStr != null && !typeStr.trim().isEmpty() && !"ALL".equalsIgnoreCase(typeStr)) {
            try {
                type = RoomType.valueOf(typeStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        Boolean active = null;
        Boolean underMaintenance = null;
        String selectedStatus = statusStr != null ? statusStr.trim().toUpperCase() : "ALL";
        if ("ACTIVE".equalsIgnoreCase(selectedStatus)) {
            active = true;
            underMaintenance = false;
        } else if ("INACTIVE".equalsIgnoreCase(selectedStatus)) {
            active = false;
        } else if ("MAINTENANCE".equalsIgnoreCase(selectedStatus)) {
            underMaintenance = true;
        }

        int pageNum = (page != null && page > 0) ? page : 1;
        Pageable pageable = PageRequest.of(pageNum - 1, 10);
        Page<Room> roomPage = roomService.searchRoomsForAdmin(query, type, active, underMaintenance, pageable);

        model.addAttribute("rooms", roomPage.getContent());
        model.addAttribute("page", roomPage);
        model.addAttribute("query", query != null ? query.trim() : "");
        model.addAttribute("selectedType", type != null ? type.name() : "ALL");
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("status", selectedStatus);
        model.addAttribute("roomTypes", RoomType.values());
        model.addAttribute("totalCount", roomPage.getTotalElements());
        model.addAttribute("activePage", "admin");
        return "admin/rooms/list";
    }

    /**
     * GET /admin/rooms/new — displays form to create a new room.
     */
    @GetMapping("/new")
    public String newRoomForm(Model model) {
        model.addAttribute("roomForm", new RoomForm());
        model.addAttribute("roomTypes", RoomType.values());
        model.addAttribute("accommodationTypes", com.stayease.model.AccommodationType.values());
        model.addAttribute("isEdit", false);
        model.addAttribute("activePage", "admin");
        return "admin/rooms/form";
    }

    /**
     * POST /admin/rooms — processes room creation.
     */
    @PostMapping
    public String createRoom(@Valid @ModelAttribute("roomForm") RoomForm roomForm,
                             BindingResult bindingResult,
                             Principal principal,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roomTypes", RoomType.values());
            model.addAttribute("accommodationTypes", com.stayease.model.AccommodationType.values());
            model.addAttribute("isEdit", false);
            model.addAttribute("activePage", "admin");
            return "admin/rooms/form";
        }

        try {
            Room created = roomService.create(roomForm);
            if (auditLogService != null) {
                String adminEmail = principal != null ? principal.getName() : "ADMIN";
                auditLogService.log(adminEmail, "ROOM_CREATED", "ROOM", String.valueOf(created.getId()),
                        "Created room #" + created.getRoomNumber() + " (" + created.getType() + ", capacity " + created.getCapacity() + ")");
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Room " + roomForm.getRoomNumber().trim() + " created successfully.");
            return "redirect:/admin/rooms";
        } catch (DuplicateRoomNumberException ex) {
            bindingResult.rejectValue("roomNumber", "duplicate", ex.getMessage());
            model.addAttribute("roomTypes", RoomType.values());
            model.addAttribute("accommodationTypes", com.stayease.model.AccommodationType.values());
            model.addAttribute("isEdit", false);
            model.addAttribute("activePage", "admin");
            return "admin/rooms/form";
        }
    }

    /**
     * GET /admin/rooms/{id}/edit — displays form to edit an existing room.
     */
    @GetMapping("/{id}/edit")
    public String editRoomForm(@PathVariable Long id, Model model) {
        Room room = roomService.getForEdit(id);
        model.addAttribute("roomForm", RoomForm.from(room));
        model.addAttribute("roomId", id);
        model.addAttribute("roomTypes", RoomType.values());
        model.addAttribute("accommodationTypes", com.stayease.model.AccommodationType.values());
        model.addAttribute("isEdit", true);
        model.addAttribute("activePage", "admin");
        return "admin/rooms/form";
    }

    /**
     * POST /admin/rooms/{id} — processes room update.
     */
    @PostMapping("/{id}")
    public String updateRoom(@PathVariable Long id,
                             @Valid @ModelAttribute("roomForm") RoomForm roomForm,
                             BindingResult bindingResult,
                             Principal principal,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roomId", id);
            model.addAttribute("roomTypes", RoomType.values());
            model.addAttribute("accommodationTypes", com.stayease.model.AccommodationType.values());
            model.addAttribute("isEdit", true);
            model.addAttribute("activePage", "admin");
            return "admin/rooms/form";
        }

        try {
            Room updated = roomService.update(id, roomForm);
            if (auditLogService != null) {
                String adminEmail = principal != null ? principal.getName() : "ADMIN";
                auditLogService.log(adminEmail, "ROOM_UPDATED", "ROOM", String.valueOf(updated.getId()),
                        "Updated room #" + updated.getRoomNumber() + " (" + updated.getType() + ", capacity " + updated.getCapacity() + ")");
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Room " + roomForm.getRoomNumber().trim() + " updated successfully.");
            return "redirect:/admin/rooms";
        } catch (DuplicateRoomNumberException ex) {
            bindingResult.rejectValue("roomNumber", "duplicate", ex.getMessage());
            model.addAttribute("roomId", id);
            model.addAttribute("roomTypes", RoomType.values());
            model.addAttribute("accommodationTypes", com.stayease.model.AccommodationType.values());
            model.addAttribute("isEdit", true);
            model.addAttribute("activePage", "admin");
            return "admin/rooms/form";
        }
    }

    /**
     * POST /admin/rooms/{id}/delete — deletes a room or refuses if bookings exist.
     */
    @PostMapping("/{id}/delete")
    public String deleteRoom(@PathVariable Long id,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        try {
            roomService.delete(id);
            if (auditLogService != null) {
                String adminEmail = principal != null ? principal.getName() : "ADMIN";
                auditLogService.log(adminEmail, "ROOM_DELETED", "ROOM", String.valueOf(id),
                        "Deleted room ID #" + id);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Room deleted successfully.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/rooms";
    }
}

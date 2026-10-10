package com.stayease.controller;

import com.stayease.dto.RoomForm;
import com.stayease.exception.DuplicateRoomNumberException;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Administrative controller for room management (CRUD).
 */
@Controller
@RequestMapping("/admin/rooms")
public class AdminRoomController {

    private final RoomService roomService;

    public AdminRoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * GET /admin/rooms — lists all rooms including inactive.
     */
    @GetMapping
    public String listRooms(Model model) {
        List<Room> rooms = roomService.findAllForAdmin();
        model.addAttribute("rooms", rooms);
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
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roomTypes", RoomType.values());
            model.addAttribute("isEdit", false);
            model.addAttribute("activePage", "admin");
            return "admin/rooms/form";
        }

        try {
            roomService.create(roomForm);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Room " + roomForm.getRoomNumber().trim() + " created successfully.");
            return "redirect:/admin/rooms";
        } catch (DuplicateRoomNumberException ex) {
            bindingResult.rejectValue("roomNumber", "duplicate", ex.getMessage());
            model.addAttribute("roomTypes", RoomType.values());
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
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roomId", id);
            model.addAttribute("roomTypes", RoomType.values());
            model.addAttribute("isEdit", true);
            model.addAttribute("activePage", "admin");
            return "admin/rooms/form";
        }

        try {
            roomService.update(id, roomForm);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Room " + roomForm.getRoomNumber().trim() + " updated successfully.");
            return "redirect:/admin/rooms";
        } catch (DuplicateRoomNumberException ex) {
            bindingResult.rejectValue("roomNumber", "duplicate", ex.getMessage());
            model.addAttribute("roomId", id);
            model.addAttribute("roomTypes", RoomType.values());
            model.addAttribute("isEdit", true);
            model.addAttribute("activePage", "admin");
            return "admin/rooms/form";
        }
    }

    /**
     * POST /admin/rooms/{id}/delete — deletes a room or refuses if bookings exist.
     */
    @PostMapping("/{id}/delete")
    public String deleteRoom(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            roomService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Room deleted successfully.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/rooms";
    }
}

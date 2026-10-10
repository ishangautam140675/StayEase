package com.stayease.controller;

import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.service.RoomService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller for public browsing and detail views of accommodations.
 */
@Controller
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * GET /rooms — lists active rooms matching optional search criteria.
     */
    @GetMapping("/rooms")
    public String listRooms(
            @RequestParam(required = false) RoomType type,
            @RequestParam(required = false) Integer capacity,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Model model) {

        List<Room> rooms = roomService.searchActiveRooms(type, capacity, minPrice, maxPrice);

        model.addAttribute("rooms", rooms);
        model.addAttribute("roomTypes", RoomType.values());
        model.addAttribute("type", type);
        model.addAttribute("capacity", capacity);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("activePage", "rooms");

        return "rooms/list";
    }

    /**
     * GET /rooms/{id} — displays details for a specific active room.
     */
    @GetMapping("/rooms/{id}")
    public String roomDetails(@PathVariable Long id, Model model) {
        Room room = roomService.getActiveRoom(id);
        model.addAttribute("room", room);
        model.addAttribute("activePage", "rooms");
        return "rooms/details";
    }
}

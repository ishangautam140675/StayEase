package com.stayease.controller;

import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.service.RoomService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
     * GET /rooms — lists active rooms matching optional search criteria (9 per page).
     * Supports advanced room search: room type, capacity, price range, amenity, and date availability.
     */
    @GetMapping("/rooms")
    public String listRooms(
            @RequestParam(required = false) RoomType type,
            @RequestParam(required = false) Integer capacity,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String amenity,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate checkIn,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate checkOut,
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            Model model) {

        int pageNum = (page != null && page > 0) ? page : 1;
        int pageIndex = pageNum - 1;
        Pageable pageable = PageRequest.of(pageIndex, 9, Sort.by("roomNumber").ascending());
        Page<Room> roomPage = roomService.searchRoomsAdvanced(type, capacity, minPrice, maxPrice, amenity, checkIn, checkOut, pageable);

        model.addAttribute("rooms", roomPage.getContent());
        model.addAttribute("page", roomPage);
        model.addAttribute("roomTypes", RoomType.values());
        model.addAttribute("type", type);
        model.addAttribute("capacity", capacity);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("amenity", amenity);
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("today", java.time.LocalDate.now());
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

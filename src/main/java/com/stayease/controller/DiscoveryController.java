package com.stayease.controller;

import com.stayease.dto.DiscoveryResponse;
import com.stayease.service.places.LocationDiscoveryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for public real-world accommodation discovery and location intelligence.
 */
@Controller
@RequestMapping("/discovery")
public class DiscoveryController {

    private final LocationDiscoveryService discoveryService;

    public DiscoveryController(LocationDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * GET /discovery
     * Displays the interactive accommodation discovery portal with manual search and GPS location.
     */
    @GetMapping
    public String discoveryPortal(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "radius", required = false, defaultValue = "10") Integer radius,
            @RequestParam(value = "gps", required = false, defaultValue = "false") Boolean gps,
            Model model) {

        // If neither query nor coordinates are provided, do not restrict to any single city.
        // Instead, discover globally across all inventory and allow worldwide search and GPS discovery.
        String effectiveQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;

        DiscoveryResponse response = discoveryService.discover(
                effectiveQuery,
                lat,
                lng,
                radius,
                Boolean.TRUE.equals(gps)
        );

        model.addAttribute("discovery", response);
        model.addAttribute("query", query != null ? query : "");
        model.addAttribute("lat", lat);
        model.addAttribute("lng", lng);
        model.addAttribute("radius", radius != null ? radius : 10);
        model.addAttribute("gps", Boolean.TRUE.equals(gps));
        model.addAttribute("activePage", "discovery");

        return "discovery/index";
    }
}

package com.stayease.controller;

import com.stayease.dto.DiscoveryResponse;
import com.stayease.service.places.LocationDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API providing location intelligence and accommodation discovery.
 */
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryApiController {

    private final LocationDiscoveryService discoveryService;

    public DiscoveryApiController(LocationDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * GET /api/discovery
     * Performs location-based discovery of StayEase properties and external accommodation POIs.
     */
    @GetMapping
    public ResponseEntity<DiscoveryResponse> discover(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "radius", required = false, defaultValue = "10") Integer radius,
            @RequestParam(value = "gps", required = false, defaultValue = "false") Boolean gps) {

        DiscoveryResponse response = discoveryService.discover(query, lat, lng, radius, Boolean.TRUE.equals(gps));
        return ResponseEntity.ok(response);
    }
}

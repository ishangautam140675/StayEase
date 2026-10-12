package com.stayease.controller;

import com.stayease.dto.AccommodationInquiryRequest;
import com.stayease.dto.DiscoveryResponse;
import com.stayease.model.AccommodationInquiry;
import com.stayease.service.AccommodationInquiryService;
import com.stayease.service.places.LocationDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API providing location intelligence, accommodation discovery, and reservation inquiries.
 */
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryApiController {

    private final LocationDiscoveryService discoveryService;
    private final AccommodationInquiryService inquiryService;

    public DiscoveryApiController(LocationDiscoveryService discoveryService,
                                  AccommodationInquiryService inquiryService) {
        this.discoveryService = discoveryService;
        this.inquiryService = inquiryService;
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

    /**
     * GET /api/discovery/suggest
     * Returns typeahead location suggestions matching a keyword query (Google Maps / Uber style).
     */
    @GetMapping("/suggest")
    public ResponseEntity<java.util.List<com.stayease.dto.LocationSuggestion>> suggest(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "limit", required = false, defaultValue = "6") Integer limit) {

        return ResponseEntity.ok(discoveryService.suggestLocations(query, limit != null ? limit : 6));
    }

    /**
     * POST /api/discovery/inquire
     * Submits an accommodation reservation inquiry, persists it, and notifies admins in real-time.
     */
    @PostMapping("/inquire")
    public ResponseEntity<Map<String, Object>> submitInquiry(
            @RequestBody AccommodationInquiryRequest request,
            Authentication authentication) {

        AccommodationInquiry saved = inquiryService.createInquiry(request, authentication);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("id", saved.getId());
        response.put("hotelName", saved.getHotelName());
        response.put("message", "Inquiry submitted successfully. Accommodation concierge and admins have been notified.");

        return ResponseEntity.ok(response);
    }
}


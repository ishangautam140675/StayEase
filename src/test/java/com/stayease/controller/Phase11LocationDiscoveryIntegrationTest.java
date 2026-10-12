package com.stayease.controller;

import com.stayease.dto.RoomForm;
import com.stayease.model.AccommodationType;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.repository.RoomRepository;
import com.stayease.service.RoomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 11: Real-World Accommodation Discovery & Location Intelligence Integration Tests")
class Phase11LocationDiscoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomService roomService;

    @Test
    @DisplayName("GET /discovery renders public accommodation discovery portal successfully")
    void testPublicDiscoveryPortalView() throws Exception {
        mockMvc.perform(get("/discovery"))
                .andExpect(status().isOk())
                .andExpect(view().name("discovery/index"))
                .andExpect(model().attributeExists("discovery"))
                .andExpect(content().string(containsString("Find Hostels")))
                .andExpect(content().string(containsString("StayEase Verified Accommodations")))
                .andExpect(content().string(containsString("External Accommodation Businesses")));
    }

    @Test
    @DisplayName("GET /api/discovery returns valid JSON with StayEase and external sections")
    void testDiscoveryJsonApi() throws Exception {
        mockMvc.perform(get("/api/discovery")
                        .param("query", "Bengaluru")
                        .param("radius", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.stayEaseListings").isArray())
                .andExpect(jsonPath("$.externalListings").isArray())
                .andExpect(jsonPath("$.radiusKm").value(10));
    }

    @Test
    @DisplayName("Permissions-Policy header is configured on responses")
    void testPermissionsPolicyHeaderPresent() throws Exception {
        mockMvc.perform(get("/discovery"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    @WithMockUser(username = "admin@stayease.com", roles = {"ADMIN"})
    @DisplayName("Admin can create property listing with real-world location metadata and verified status")
    void testAdminCreatePropertyListingWithLocationMetadata() throws Exception {
        mockMvc.perform(post("/admin/rooms")
                        .with(csrf())
                        .param("roomNumber", "P11-901")
                        .param("type", "SINGLE")
                        .param("capacity", "1")
                        .param("pricePerNight", "600.00")
                        .param("description", "Phase 11 real-world student hostel room")
                        .param("amenities", "Wi-Fi, AC, Study Desk")
                        .param("propertyName", "Shivaji Nagar Scholars Hostel")
                        .param("accommodationType", "HOSTEL")
                        .param("address", "15 Commercial Street, Shivaji Nagar")
                        .param("locality", "Shivaji Nagar")
                        .param("city", "Bengaluru")
                        .param("postalCode", "560001")
                        .param("latitude", "12.9856")
                        .param("longitude", "77.6057")
                        .param("monthlyRent", "9000.00")
                        .param("verified", "true")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/rooms"));

        Room created = roomRepository.findByRoomNumberIgnoreCase("P11-901").orElse(null);
        assertNotNull(created, "Created room must exist in repository");
        assertEquals("Shivaji Nagar Scholars Hostel", created.getPropertyName());
        assertEquals(AccommodationType.HOSTEL, created.getAccommodationType());
        assertEquals("Shivaji Nagar", created.getLocality());
        assertEquals("Bengaluru", created.getCity());
        assertEquals(12.9856, created.getLatitude(), 0.0001);
        assertEquals(77.6057, created.getLongitude(), 0.0001);
        assertEquals(new BigDecimal("9000.00"), created.getMonthlyRent());
        assertTrue(created.isVerified(), "Property must be marked verified");
        assertNotNull(created.getLastVerifiedAt(), "Verification timestamp must be populated");
    }

    @Test
    @WithMockUser(username = "customer@stayease.com", roles = {"CUSTOMER"})
    @DisplayName("Non-admin customer is forbidden from creating property listings")
    void testCustomerForbiddenFromAdminRoomManagement() throws Exception {
        mockMvc.perform(post("/admin/rooms")
                        .with(csrf())
                        .param("roomNumber", "HACK-999")
                        .param("type", "SINGLE")
                        .param("capacity", "1")
                        .param("pricePerNight", "500.00"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@stayease.com", roles = {"ADMIN"})
    @DisplayName("Admin can update property listing and unverified status is respected")
    void testAdminUpdatePropertyVerificationStatus() throws Exception {
        RoomForm form = new RoomForm("P11-902", RoomType.DOUBLE, 2, new BigDecimal("800.00"), "Unverified PG", true);
        form.setPropertyName("Unverified Test PG");
        form.setAccommodationType(AccommodationType.PG);
        form.setLocality("HSR Layout");
        form.setCity("Bengaluru");
        form.setVerified(false);
        Room room = roomService.create(form);

        mockMvc.perform(post("/admin/rooms/" + room.getId())
                        .with(csrf())
                        .param("roomNumber", "P11-902")
                        .param("type", "DOUBLE")
                        .param("capacity", "2")
                        .param("pricePerNight", "850.00")
                        .param("description", "Updated verified PG")
                        .param("propertyName", "Verified HSR Luxury PG")
                        .param("accommodationType", "PG")
                        .param("locality", "HSR Layout")
                        .param("city", "Bengaluru")
                        .param("verified", "true")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/rooms"));

        Room updated = roomRepository.findById(room.getId()).orElseThrow();
        assertEquals("Verified HSR Luxury PG", updated.getPropertyName());
        assertTrue(updated.isVerified());
        assertNotNull(updated.getLastVerifiedAt());
    }

    @Test
    @DisplayName("GET /api/discovery/suggest returns location suggestions list (Google Maps / Cab booking style)")
    void testDiscoverySuggestApi() throws Exception {
        mockMvc.perform(get("/api/discovery/suggest")
                        .param("query", "Jaipur")
                        .param("limit", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }
}

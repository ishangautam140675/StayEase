package com.stayease.controller;

import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Room activeRoom;
    private Room inactiveRoom;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        roomRepository.flush();

        activeRoom = roomRepository.save(new Room(
                "A101", RoomType.SINGLE, 1, new BigDecimal("650.00"), "Active room", true
        ));
        inactiveRoom = roomRepository.save(new Room(
                "A102", RoomType.DOUBLE, 2, new BigDecimal("950.00"), "Inactive room", false
        ));
        roomRepository.flush();
    }

    // ── Route Protection (Anonymous & Customer) ────────────────────────────

    @Test
    @DisplayName("Anonymous users are redirected to /login on every /admin/rooms route")
    void anonymous_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/rooms"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/admin/rooms/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/admin/rooms/{id}/edit", activeRoom.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(post("/admin/rooms").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(post("/admin/rooms/{id}", activeRoom.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(post("/admin/rooms/{id}/delete", activeRoom.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("CUSTOMER role gets 403 Forbidden on every /admin/rooms route")
    void customer_returns403() throws Exception {
        mockMvc.perform(get("/admin/rooms"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/rooms/new"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/rooms/{id}/edit", activeRoom.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/rooms").with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/rooms/{id}", activeRoom.getId()).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/rooms/{id}/delete", activeRoom.getId()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── Admin Operations ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN can create a room with a valid POST (csrf)")
    void admin_createRoom_validPost_redirects() throws Exception {
        mockMvc.perform(post("/admin/rooms")
                        .with(csrf())
                        .param("roomNumber", "A103")
                        .param("type", "TRIPLE")
                        .param("capacity", "3")
                        .param("pricePerNight", "1200.00")
                        .param("description", "Brand new triple room")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/rooms"))
                .andExpect(flash().attributeExists("successMessage"));

        assertThat(roomRepository.findByRoomNumberIgnoreCase("A103")).isPresent();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Invalid form returns 200 with validation error messages")
    void admin_createRoom_invalidForm_returns200WithError() throws Exception {
        mockMvc.perform(post("/admin/rooms")
                        .with(csrf())
                        .param("roomNumber", "")
                        .param("capacity", "0")
                        .param("pricePerNight", "0.50"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/rooms/form"))
                .andExpect(content().string(containsString("Room number is required")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin rooms list shows inactive rooms, while public /rooms hides them")
    void adminList_showsInactiveRooms_publicHidesThem() throws Exception {
        // Admin list shows both active and inactive
        mockMvc.perform(get("/admin/rooms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A101")))
                .andExpect(content().string(containsString("A102")))
                .andExpect(content().string(containsString("Inactive")));

        // Public /rooms hides inactive room
        mockMvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A101")))
                .andExpect(content().string(not(containsString("A102"))));
    }
}

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Room room101;
    private Room room102;
    private Room roomInactive;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        roomRepository.flush();

        room101 = roomRepository.save(new Room(
                "C101", RoomType.SINGLE, 1, new BigDecimal("650.00"), "Comfortable Single Room", true
        ));

        room102 = roomRepository.save(new Room(
                "C102", RoomType.DOUBLE, 2, new BigDecimal("950.00"), "Deluxe Double Room", true
        ));

        roomInactive = roomRepository.save(new Room(
                "C103", RoomType.SINGLE, 1, new BigDecimal("600.00"), "Offline Room", false
        ));
        roomRepository.flush();
    }

    @Test
    @DisplayName("Anonymous GET /rooms returns 200 and shows active room numbers")
    void getRooms_anonymous_returns200AndShowsRooms() throws Exception {
        mockMvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("C101")))
                .andExpect(content().string(containsString("C102")))
                .andExpect(content().string(not(containsString("C103"))));
    }

    @Test
    @DisplayName("GET /rooms with filter reduces the room list")
    void getRooms_withTypeFilter_reducesList() throws Exception {
        mockMvc.perform(get("/rooms").param("type", "DOUBLE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("C102")))
                .andExpect(content().string(not(containsString("C101"))));
    }

    @Test
    @DisplayName("GET /rooms/{id} shows the room details and price")
    void getRoomDetails_validId_showsDetailsAndPrice() throws Exception {
        mockMvc.perform(get("/rooms/{id}", room101.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("C101")))
                .andExpect(content().string(containsString("650.00")));
    }

    @Test
    @DisplayName("GET /rooms/{id} with unknown id returns 404")
    void getRoomDetails_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/rooms/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /rooms/{id} with inactive id returns 404")
    void getRoomDetails_inactiveId_returns404() throws Exception {
        mockMvc.perform(get("/rooms/{id}", roomInactive.getId()))
                .andExpect(status().isNotFound());
    }
}

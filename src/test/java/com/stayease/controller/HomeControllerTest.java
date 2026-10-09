package com.stayease.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for HomeController navigation, shared fragments, and view rendering.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homePage_returns200AndContainsStayEase() throws Exception {
        mockMvc.perform(get("/"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("StayEase")));
    }

    @Test
    void homePage_anonymousUser_containsBrowseRoomsAndLogin() throws Exception {
        mockMvc.perform(get("/"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Browse rooms")))
               .andExpect(content().string(containsString("Login")));
    }

    @Test
    void roomsPage_returns200() throws Exception {
        mockMvc.perform(get("/rooms"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Explore Accommodations")));
    }

    @Test
    void customerDashboard_returns200() throws Exception {
        mockMvc.perform(get("/customer/dashboard"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Customer Dashboard")));
    }

    @Test
    void adminDashboard_returns200() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Administrator Dashboard")));
    }
}

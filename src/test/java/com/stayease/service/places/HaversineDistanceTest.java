package com.stayease.service.places;

import com.stayease.model.Room;
import com.stayease.model.RoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 11: Haversine Distance & Coordinate Unit Tests")
class HaversineDistanceTest {

    @Test
    @DisplayName("Calculates accurate distance between known Koramangala and Indiranagar coordinates")
    void testAccurateDistanceBetweenKnownPoints() {
        Room room = new Room("101", RoomType.SINGLE, 1, new BigDecimal("500.00"), "Test Room", true);
        // Koramangala 4th Block
        room.setLatitude(12.9352);
        room.setLongitude(77.6245);

        // User at Indiranagar (approx ~5.1 km)
        Double distance = room.calculateDistanceKm(12.9784, 77.6408);
        assertNotNull(distance);
        assertTrue(distance >= 4.9 && distance <= 5.5, "Expected distance around 5.1 km, got: " + distance);
    }

    @Test
    @DisplayName("Returns 0.0 km for identical coordinates")
    void testZeroDistanceForSameCoordinates() {
        Room room = new Room("101", RoomType.SINGLE, 1, new BigDecimal("500.00"), "Test Room", true);
        room.setLatitude(12.9352);
        room.setLongitude(77.6245);

        Double distance = room.calculateDistanceKm(12.9352, 77.6245);
        assertNotNull(distance);
        assertEquals(0.0, distance, 0.01);
    }

    @Test
    @DisplayName("Returns null gracefully when property coordinates are missing")
    void testNullWhenPropertyCoordinatesMissing() {
        Room room = new Room("101", RoomType.SINGLE, 1, new BigDecimal("500.00"), "Test Room", true);
        room.setLatitude(null);
        room.setLongitude(null);

        assertNull(room.calculateDistanceKm(12.9352, 77.6245));
        assertFalse(room.isLocationAvailable());
    }

    @Test
    @DisplayName("Returns null gracefully when user coordinates are missing")
    void testNullWhenUserCoordinatesMissing() {
        Room room = new Room("101", RoomType.SINGLE, 1, new BigDecimal("500.00"), "Test Room", true);
        room.setLatitude(12.9352);
        room.setLongitude(77.6245);

        assertNull(room.calculateDistanceKm(null, 77.6245));
        assertNull(room.calculateDistanceKm(12.9352, null));
        assertNull(room.calculateDistanceKm(null, null));
    }
}

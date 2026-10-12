package com.stayease.service.places;

import com.stayease.dto.Coordinates;
import com.stayease.dto.DiscoveryResponse;
import com.stayease.dto.ExternalDiscoveryResult;
import com.stayease.dto.StayEaseListingItem;
import com.stayease.model.AccommodationType;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 11: LocationDiscoveryService Unit & Boundary Tests")
class LocationDiscoveryServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PlacesProvider placesProvider;

    private LocationDiscoveryService discoveryService;

    private Room koramangalaRoom;
    private Room indiranagarRoom;

    @BeforeEach
    void setUp() {
        discoveryService = new LocationDiscoveryService(roomRepository, placesProvider);

        koramangalaRoom = new Room("101", RoomType.SINGLE, 1, new BigDecimal("550.00"), "Koramangala Room", true);
        koramangalaRoom.setId(1L);
        koramangalaRoom.setPropertyName("Greenview Student Hostel");
        koramangalaRoom.setAccommodationType(AccommodationType.HOSTEL);
        koramangalaRoom.setAddress("12th Cross, Koramangala 4th Block");
        koramangalaRoom.setLocality("Koramangala");
        koramangalaRoom.setCity("Bengaluru");
        koramangalaRoom.setLatitude(12.9352);
        koramangalaRoom.setLongitude(77.6245);
        koramangalaRoom.setMonthlyRent(new BigDecimal("8500.00"));
        koramangalaRoom.setVerified(true);

        indiranagarRoom = new Room("102", RoomType.DOUBLE, 2, new BigDecimal("850.00"), "Indiranagar Room", true);
        indiranagarRoom.setId(2L);
        indiranagarRoom.setPropertyName("StayEase Luxury PG for Women");
        indiranagarRoom.setAccommodationType(AccommodationType.PG);
        indiranagarRoom.setAddress("80 Feet Road, Indiranagar");
        indiranagarRoom.setLocality("Indiranagar");
        indiranagarRoom.setCity("Bengaluru");
        indiranagarRoom.setLatitude(12.9784);
        indiranagarRoom.setLongitude(77.6408);
        indiranagarRoom.setMonthlyRent(new BigDecimal("12000.00"));
        indiranagarRoom.setVerified(true);
    }

    @Test
    @DisplayName("Discovers StayEase properties via manual text search for locality")
    void testDiscoverByLocalityQuery() {
        when(roomRepository.findAll()).thenReturn(List.of(koramangalaRoom, indiranagarRoom));
        when(placesProvider.isAvailable()).thenReturn(false);

        DiscoveryResponse response = discoveryService.discover("Koramangala", null, null, 10, false);

        assertNotNull(response);
        assertEquals(1, response.getStayEaseListings().size());
        StayEaseListingItem item = response.getStayEaseListings().get(0);
        assertEquals("Greenview Student Hostel", item.getPropertyName());
        assertEquals("Koramangala", item.getLocality());
        assertTrue(item.isBookable());
        assertTrue(item.isVerified());
    }

    @Test
    @DisplayName("Filters properties by radius from given coordinates and sorts closest first")
    void testRadiusFilteringFromCoordinates() {
        when(roomRepository.findAll()).thenReturn(List.of(koramangalaRoom, indiranagarRoom));
        when(placesProvider.isAvailable()).thenReturn(false);

        // Search near Koramangala with 3km radius (Indiranagar is ~5.1km away so must be excluded)
        DiscoveryResponse response = discoveryService.discover(null, 12.9350, 77.6240, 3, true);

        assertNotNull(response);
        assertEquals(1, response.getStayEaseListings().size());
        assertEquals("Greenview Student Hostel", response.getStayEaseListings().get(0).getPropertyName());
        assertNotNull(response.getStayEaseListings().get(0).getDistanceKm());
        assertTrue(response.getStayEaseListings().get(0).getDistanceKm() <= 3.0);
    }

    @Test
    @DisplayName("External provider POIs are normalized with bookableOnStayEase = false")
    void testExternalDiscoveryNormalizationAndNonBookable() {
        when(roomRepository.findAll()).thenReturn(List.of(koramangalaRoom));
        when(placesProvider.isAvailable()).thenReturn(true);
        when(placesProvider.getProviderName()).thenReturn("OpenStreetMap");
        when(placesProvider.getAttribution()).thenReturn("© OpenStreetMap contributors");

        ExternalDiscoveryResult ext = new ExternalDiscoveryResult(
                "osm-node-9999",
                "St. Joseph's Youth Hostel",
                "Hostel",
                "Hosur Road, Bengaluru",
                "Adugodi",
                "Bengaluru",
                12.9400,
                77.6100,
                1.8,
                "OpenStreetMap",
                "© OpenStreetMap contributors",
                "https://www.openstreetmap.org"
        );
        when(placesProvider.searchNearbyAccommodations(12.9352, 77.6245, 10)).thenReturn(List.of(ext));

        DiscoveryResponse response = discoveryService.discover(null, 12.9352, 77.6245, 10, true);

        assertEquals(1, response.getStayEaseListings().size());
        assertEquals(1, response.getExternalListings().size());

        ExternalDiscoveryResult extResult = response.getExternalListings().get(0);
        assertEquals("St. Joseph's Youth Hostel", extResult.getName());
        assertFalse(extResult.isBookableOnStayEase(), "External listings must never be bookable on StayEase");
        assertEquals("OpenStreetMap", extResult.getProviderSource());
        assertEquals("© OpenStreetMap contributors", response.getAttribution());
    }

    @Test
    @DisplayName("Deduplicates external business overlapping with StayEase verified inventory")
    void testDeduplicationOfOverlappingExternalBusiness() {
        when(roomRepository.findAll()).thenReturn(List.of(koramangalaRoom));
        when(placesProvider.isAvailable()).thenReturn(true);
        when(placesProvider.getProviderName()).thenReturn("OpenStreetMap");
        when(placesProvider.getAttribution()).thenReturn("© OpenStreetMap contributors");

        // External POI with same coordinates (within 80m) or same name as Greenview Student Hostel
        ExternalDiscoveryResult duplicateExt = new ExternalDiscoveryResult(
                "osm-node-101",
                "Greenview Student Hostel",
                "Hostel",
                "12th Cross, Koramangala",
                "Koramangala",
                "Bengaluru",
                12.93521, // ~1 meter away
                77.62451,
                0.01,
                "OpenStreetMap",
                "© OpenStreetMap contributors",
                "https://www.openstreetmap.org"
        );

        ExternalDiscoveryResult distinctExt = new ExternalDiscoveryResult(
                "osm-node-202",
                "Independent Backpacker PG",
                "Hostel",
                "Outer Ring Road",
                "Koramangala",
                "Bengaluru",
                12.9450,
                77.6350,
                1.5,
                "OpenStreetMap",
                "© OpenStreetMap contributors",
                "https://www.openstreetmap.org"
        );

        when(placesProvider.searchNearbyAccommodations(12.9352, 77.6245, 10))
                .thenReturn(List.of(duplicateExt, distinctExt));

        DiscoveryResponse response = discoveryService.discover(null, 12.9352, 77.6245, 10, true);

        // StayEase listing is preserved
        assertEquals(1, response.getStayEaseListings().size());
        // Duplicate is removed, only the distinct external business is retained
        assertEquals(1, response.getExternalListings().size());
        assertEquals("Independent Backpacker PG", response.getExternalListings().get(0).getName());
    }

    @Test
    @DisplayName("Handles external provider exception gracefully without failing StayEase listings")
    void testExternalProviderFailureResilience() {
        when(roomRepository.findAll()).thenReturn(List.of(koramangalaRoom));
        when(placesProvider.isAvailable()).thenReturn(true);
        when(placesProvider.searchNearbyAccommodations(anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new RuntimeException("Overpass API Gateway Timeout"));

        DiscoveryResponse response = discoveryService.discover(null, 12.9352, 77.6245, 10, true);

        assertNotNull(response);
        assertEquals(1, response.getStayEaseListings().size(), "StayEase listings must remain intact during external provider outages");
        assertTrue(response.getExternalListings().isEmpty());
        assertNotNull(response.getNoticeMessage());
        assertTrue(response.getNoticeMessage().contains("temporarily unavailable"));
    }

    @Test
    @DisplayName("Geocodes location query when coordinates are not provided")
    void testGeocodingForTextQuery() {
        when(roomRepository.findAll()).thenReturn(List.of(koramangalaRoom));
        when(placesProvider.isAvailable()).thenReturn(true);
        when(placesProvider.geocode("Koramangala"))
                .thenReturn(Optional.of(new Coordinates(12.9352, 77.6245, "Koramangala, Bengaluru, Karnataka, India")));
        when(placesProvider.searchNearbyAccommodations(12.9352, 77.6245, 10)).thenReturn(List.of());

        DiscoveryResponse response = discoveryService.discover("Koramangala", null, null, 10, false);

        assertNotNull(response);
        assertEquals(12.9352, response.getSearchLatitude());
        assertEquals(77.6245, response.getSearchLongitude());
        assertEquals("Koramangala, Bengaluru, Karnataka, India", response.getResolvedLocationName());
    }

    @Test
    @DisplayName("Reverse geocodes device GPS coordinates anywhere globally (e.g. Jaipur)")
    void testWorldwideReverseGeocodingForGpsCoordinates() {
        when(roomRepository.findAll()).thenReturn(List.of());
        when(placesProvider.isAvailable()).thenReturn(true);
        when(placesProvider.reverseGeocode(26.9124, 75.7873))
                .thenReturn(Optional.of("Malviya Nagar, Jaipur, Rajasthan, India"));
        when(placesProvider.searchNearbyAccommodations(26.9124, 75.7873, 15)).thenReturn(List.of());

        DiscoveryResponse response = discoveryService.discover(null, 26.9124, 75.7873, 15, true);

        assertNotNull(response);
        assertEquals(26.9124, response.getSearchLatitude());
        assertEquals(75.7873, response.getSearchLongitude());
        assertEquals("Malviya Nagar, Jaipur, Rajasthan, India", response.getResolvedLocationName());
        assertTrue(response.isGpsUsed());
        assertEquals(15, response.getRadiusKm());
    }

    @Test
    @DisplayName("Supports extended search radius up to 100 km")
    void testRadiusClampedUpTo100Km() {
        when(roomRepository.findAll()).thenReturn(List.of());
        when(placesProvider.isAvailable()).thenReturn(true);
        when(placesProvider.searchNearbyAccommodations(anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());

        DiscoveryResponse response75 = discoveryService.discover(null, 26.9124, 75.7873, 75, true);
        assertEquals(75, response75.getRadiusKm(), "Radius of 75km should be preserved");

        DiscoveryResponse responseOver100 = discoveryService.discover(null, 26.9124, 75.7873, 250, true);
        assertEquals(100, responseOver100.getRadiusKm(), "Radius exceeding 100km should clamp to 100km max");
    }
}

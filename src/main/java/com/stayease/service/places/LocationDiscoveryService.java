package com.stayease.service.places;

import com.stayease.dto.Coordinates;
import com.stayease.dto.DiscoveryResponse;
import com.stayease.dto.ExternalDiscoveryResult;
import com.stayease.dto.StayEaseListingItem;
import com.stayease.model.Room;
import com.stayease.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service orchestrating accommodation discovery across StayEase verified inventory
 * and external location intelligence providers.
 */
@Service
public class LocationDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(LocationDiscoveryService.class);

    private final RoomRepository roomRepository;
    private final PlacesProvider placesProvider;

    public LocationDiscoveryService(RoomRepository roomRepository,
                                    @Autowired(required = false) PlacesProvider placesProvider) {
        this.roomRepository = roomRepository;
        this.placesProvider = placesProvider;
    }

    /**
     * Executes accommodation discovery using manual text search and/or device coordinates.
     *
     * @param query text query (city, locality, landmark)
     * @param lat device latitude (if GPS granted)
     * @param lng device longitude (if GPS granted)
     * @param radiusKm search radius (default 10)
     * @param gpsUsed whether coordinates originated from user-authorized device GPS
     */
    public DiscoveryResponse discover(String query, Double lat, Double lng, Integer radiusKm, boolean gpsUsed) {
        int radius = (radiusKm != null && radiusKm > 0) ? Math.min(radiusKm, 100) : 10;
        DiscoveryResponse response = new DiscoveryResponse();
        response.setRadiusKm(radius);
        response.setGpsUsed(gpsUsed);
        response.setQuery(query != null ? query.trim() : null);

        Double searchLat = lat;
        Double searchLng = lng;
        String resolvedLocation = null;

        // 1. Resolve coordinates if manual query is provided without explicit GPS
        if ((searchLat == null || searchLng == null) && query != null && !query.trim().isEmpty()) {
            if (placesProvider != null && placesProvider.isAvailable()) {
                try {
                    Optional<Coordinates> coordsOpt = placesProvider.geocode(query.trim());
                    if (coordsOpt.isPresent()) {
                        Coordinates coords = coordsOpt.get();
                        searchLat = coords.latitude();
                        searchLng = coords.longitude();
                        resolvedLocation = coords.displayName();
                    }
                } catch (Exception e) {
                    log.warn("Geocoding failed for query '{}': {}", query, e.getMessage());
                }
            }
        } else if (searchLat != null && searchLng != null && (query == null || query.trim().isEmpty())) {
            // User provided GPS coordinates — reverse geocode to human readable locality/city
            if (placesProvider != null && placesProvider.isAvailable()) {
                try {
                    Optional<String> rev = placesProvider.reverseGeocode(searchLat, searchLng);
                    if (rev.isPresent()) {
                        resolvedLocation = rev.get();
                    }
                } catch (Exception e) {
                    log.debug("Reverse geocoding failed: {}", e.getMessage());
                }
            }
            if (resolvedLocation == null) {
                resolvedLocation = String.format(Locale.ROOT, "Current Location (%.4f, %.4f)", searchLat, searchLng);
            }
        }

        response.setSearchLatitude(searchLat);
        response.setSearchLongitude(searchLng);
        response.setResolvedLocationName(resolvedLocation != null ? resolvedLocation : (query != null && !query.trim().isEmpty() ? query.trim() : "All Available Locations"));

        // 2. Discover StayEase-managed inventory
        List<Room> activeRooms = roomRepository.findAll().stream()
                .filter(Room::isActive)
                .toList();

        List<StayEaseListingItem> stayEaseMatches = new ArrayList<>();

        for (Room room : activeRooms) {
            boolean matchesQuery = true;
            if (query != null && !query.trim().isEmpty()) {
                String q = query.trim().toLowerCase();
                boolean matchesLocality = room.getLocality() != null && room.getLocality().toLowerCase().contains(q);
                boolean matchesCity = room.getCity() != null && room.getCity().toLowerCase().contains(q);
                boolean matchesAddress = room.getAddress() != null && room.getAddress().toLowerCase().contains(q);
                boolean matchesPropName = room.getPropertyName() != null && room.getPropertyName().toLowerCase().contains(q);
                matchesQuery = matchesLocality || matchesCity || matchesAddress || matchesPropName;
            }

            Double dist = null;
            if (searchLat != null && searchLng != null && room.isLocationAvailable()) {
                dist = room.calculateDistanceKm(searchLat, searchLng);
            }

            // If coordinates are active, filter by radius; or include if explicitly matched by text query
            if (searchLat != null && searchLng != null) {
                if (dist != null && dist <= radius) {
                    StayEaseListingItem item = StayEaseListingItem.fromRoom(room, searchLat, searchLng);
                    stayEaseMatches.add(item);
                } else if (matchesQuery && dist == null) {
                    // Property has no coordinates yet, but matched the search keyword
                    StayEaseListingItem item = StayEaseListingItem.fromRoom(room, searchLat, searchLng);
                    stayEaseMatches.add(item);
                }
            } else if (matchesQuery) {
                StayEaseListingItem item = StayEaseListingItem.fromRoom(room, null, null);
                stayEaseMatches.add(item);
            }
        }

        // Sort StayEase listings: Verified first, then by distance (if available), then by ID
        stayEaseMatches.sort((a, b) -> {
            if (a.isVerified() != b.isVerified()) {
                return a.isVerified() ? -1 : 1;
            }
            if (a.getDistanceKm() != null && b.getDistanceKm() != null) {
                return Double.compare(a.getDistanceKm(), b.getDistanceKm());
            }
            if (a.getDistanceKm() != null) return -1;
            if (b.getDistanceKm() != null) return 1;
            return a.getId().compareTo(b.getId());
        });

        response.setStayEaseListings(stayEaseMatches);

        // 3. Discover external accommodation businesses if coordinates are available
        List<ExternalDiscoveryResult> externalResults = new ArrayList<>();
        if (searchLat != null && searchLng != null && placesProvider != null && placesProvider.isAvailable()) {
            try {
                List<ExternalDiscoveryResult> rawExternal = placesProvider.searchNearbyAccommodations(searchLat, searchLng, radius);

                // Deduplicate external results against StayEase-managed inventory (e.g. within 80 meters)
                for (ExternalDiscoveryResult ext : rawExternal) {
                    boolean isDuplicate = false;
                    for (StayEaseListingItem se : stayEaseMatches) {
                        if (se.getLatitude() != null && se.getLongitude() != null &&
                                ext.getLatitude() != null && ext.getLongitude() != null) {
                            double distBetween = calculateHaversineKm(se.getLatitude(), se.getLongitude(), ext.getLatitude(), ext.getLongitude());
                            if (distBetween < 0.08) { // within 80 meters
                                isDuplicate = true;
                                break;
                            }
                        }
                        if (ext.getName() != null && se.getPropertyName() != null
                                && ext.getName().trim().equalsIgnoreCase(se.getPropertyName().trim())) {
                            isDuplicate = true;
                            break;
                        }
                    }

                    if (!isDuplicate) {
                        externalResults.add(ext);
                    }
                }

                response.setProviderName(placesProvider.getProviderName());
                response.setAttribution(placesProvider.getAttribution());
            } catch (Exception ex) {
                log.warn("External provider discovery failed: {}", ex.getMessage());
                response.setNoticeMessage("External business discovery is temporarily unavailable. Displaying verified StayEase properties.");
            }
        }

        response.setExternalListings(externalResults);
        return response;
    }

    private double calculateHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(R * c * 100.0) / 100.0;
    }
}

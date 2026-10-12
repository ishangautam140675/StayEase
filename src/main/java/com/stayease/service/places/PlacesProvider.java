package com.stayease.service.places;

import com.stayease.dto.Coordinates;
import com.stayease.dto.ExternalDiscoveryResult;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Strategy interface for external geocoding and accommodation business discovery providers.
 */
public interface PlacesProvider {

    /**
     * Resolves a free-text location query (e.g. city, locality, landmark) into geographic coordinates.
     */
    Optional<Coordinates> geocode(String query);

    /**
     * Resolves geographic coordinates into a human-readable location name (e.g. city, locality, state).
     */
    default Optional<String> reverseGeocode(double latitude, double longitude) {
        return Optional.empty();
    }

    /**
     * Provides autocomplete / typeahead location suggestions for keyword queries.
     *
     * @param query keyword or partial location text
     * @param limit maximum suggestions to return
     * @return list of matching location suggestions
     */
    default List<com.stayease.dto.LocationSuggestion> suggestLocations(String query, int limit) {
        return Collections.emptyList();
    }

    /**
     * Searches for external accommodation businesses (hotels, hostels, PGs, guest houses) around given coordinates.
     *
     * @param latitude user or query latitude
     * @param longitude user or query longitude
     * @param radiusKm search radius in kilometers
     * @return normalized external business results
     */
    List<ExternalDiscoveryResult> searchNearbyAccommodations(double latitude, double longitude, int radiusKm);

    /**
     * Human-readable name of this provider.
     */
    String getProviderName();

    /**
     * Mandatory legal attribution string for this provider.
     */
    String getAttribution();

    /**
     * Indicates whether this provider is currently configured and operational.
     */
    boolean isAvailable();
}

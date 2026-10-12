package com.stayease.service.places;

import com.stayease.dto.Coordinates;
import com.stayease.dto.ExternalDiscoveryResult;

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
     * Searches for external accommodation businesses (hostels, PGs, guest houses) around given coordinates.
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

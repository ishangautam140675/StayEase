package com.stayease.service.places;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayease.dto.Coordinates;
import com.stayease.dto.ExternalDiscoveryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenStreetMap implementation of {@link PlacesProvider} using Nominatim for geocoding
 * and Overpass API for nearby hostel / PG / accommodation POI discovery.
 * Free, non-commercial/fair-use compliant, with server-side caching and graceful degradation.
 */
@Component
public class OpenStreetMapPlacesProvider implements PlacesProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenStreetMapPlacesProvider.class);

    private static final String USER_AGENT = "StayEase-Hostel-Discovery/1.0 (https://stayease.local; contact@stayease.local)";
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    // Lightweight in-memory caches to respect Nominatim/Overpass rate limits
    private final Map<String, CacheEntry<Optional<Coordinates>>> geocodeCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<ExternalDiscoveryResult>>> nearbyCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 600_000L; // 10 minutes

    public OpenStreetMapPlacesProvider(
            ObjectMapper objectMapper,
            @Value("${stayease.places.osm.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Optional<Coordinates> geocode(String query) {
        if (!enabled || query == null || query.trim().isEmpty()) {
            return Optional.empty();
        }

        String normalizedKey = query.trim().toLowerCase();
        CacheEntry<Optional<Coordinates>> cached = geocodeCache.get(normalizedKey);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }

        try {
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            String url = NOMINATIM_URL + "?q=" + encodedQuery + "&format=json&limit=1&addressdetails=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.isArray() && !root.isEmpty()) {
                    JsonNode first = root.get(0);
                    double lat = Double.parseDouble(first.path("lat").asText());
                    double lon = Double.parseDouble(first.path("lon").asText());
                    String displayName = first.path("display_name").asText();

                    Optional<Coordinates> result = Optional.of(new Coordinates(lat, lon, displayName));
                    geocodeCache.put(normalizedKey, new CacheEntry<>(result, System.currentTimeMillis() + CACHE_TTL_MS));
                    return result;
                }
            } else {
                log.warn("Nominatim geocoding returned status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.warn("Nominatim geocoding failed for '{}': {}", query, ex.getMessage());
        }

        Optional<Coordinates> empty = Optional.empty();
        geocodeCache.put(normalizedKey, new CacheEntry<>(empty, System.currentTimeMillis() + 60_000L));
        return empty;
    }

    @Override
    public List<ExternalDiscoveryResult> searchNearbyAccommodations(double latitude, double longitude, int radiusKm) {
        if (!enabled) {
            return Collections.emptyList();
        }

        int clampedRadiusKm = Math.min(Math.max(radiusKm, 1), 50);
        int radiusMeters = clampedRadiusKm * 1000;
        String cacheKey = String.format(Locale.ROOT, "%.3f_%.3f_%d", latitude, longitude, clampedRadiusKm);

        CacheEntry<List<ExternalDiscoveryResult>> cached = nearbyCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }

        String query = String.format(Locale.ROOT,
                "[out:json][timeout:5];(" +
                "node[\"tourism\"=\"hostel\"](around:%d,%.6f,%.6f);" +
                "node[\"amenity\"=\"student_accommodation\"](around:%d,%.6f,%.6f);" +
                "node[\"tourism\"=\"guest_house\"](around:%d,%.6f,%.6f);" +
                "way[\"tourism\"=\"hostel\"](around:%d,%.6f,%.6f);" +
                "way[\"amenity\"=\"student_accommodation\"](around:%d,%.6f,%.6f);" +
                ");out center 15;",
                radiusMeters, latitude, longitude,
                radiusMeters, latitude, longitude,
                radiusMeters, latitude, longitude,
                radiusMeters, latitude, longitude,
                radiusMeters, latitude, longitude
        );

        List<ExternalDiscoveryResult> results = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OVERPASS_URL))
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString("data=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode elements = root.path("elements");

                if (elements.isArray()) {
                    for (JsonNode el : elements) {
                        JsonNode tags = el.path("tags");
                        String name = tags.path("name").asText(null);
                        if (name == null || name.trim().isEmpty()) {
                            continue; // Skip unnamed features
                        }

                        double itemLat;
                        double itemLon;
                        if (el.has("lat") && el.has("lon")) {
                            itemLat = el.path("lat").asDouble();
                            itemLon = el.path("lon").asDouble();
                        } else if (el.has("center")) {
                            itemLat = el.path("center").path("lat").asDouble();
                            itemLon = el.path("center").path("lon").asDouble();
                        } else {
                            continue;
                        }

                        String osmType = el.path("type").asText("node");
                        long osmId = el.path("id").asLong();
                        String externalId = "osm-" + osmType + "-" + osmId;

                        String category = "Hostel";
                        if ("student_accommodation".equalsIgnoreCase(tags.path("amenity").asText())) {
                            category = "Student PG / Accommodation";
                        } else if ("guest_house".equalsIgnoreCase(tags.path("tourism").asText())) {
                            category = "Guest House / Homestay";
                        }

                        String street = tags.path("addr:street").asText("");
                        String housenumber = tags.path("addr:housenumber").asText("");
                        String city = tags.path("addr:city").asText("");
                        String postcode = tags.path("addr:postcode").asText("");

                        StringBuilder addressBuilder = new StringBuilder();
                        if (!housenumber.isEmpty()) addressBuilder.append(housenumber).append(", ");
                        if (!street.isEmpty()) addressBuilder.append(street).append(", ");
                        if (!city.isEmpty()) addressBuilder.append(city).append(" ");
                        if (!postcode.isEmpty()) addressBuilder.append("- ").append(postcode);

                        String finalAddress = addressBuilder.toString().trim();
                        if (finalAddress.isEmpty()) {
                            finalAddress = "Address details not listed on OpenStreetMap";
                        }

                        double dist = calculateHaversineKm(latitude, longitude, itemLat, itemLon);

                        ExternalDiscoveryResult res = new ExternalDiscoveryResult(
                                externalId,
                                name,
                                category,
                                finalAddress,
                                tags.path("addr:suburb").asText(city.isEmpty() ? "Nearby Locality" : city),
                                city.isEmpty() ? "Local Region" : city,
                                itemLat,
                                itemLon,
                                dist,
                                "OpenStreetMap",
                                "© OpenStreetMap contributors",
                                "https://www.openstreetmap.org/directions?to=" + itemLat + "%2C" + itemLon
                        );

                        if (tags.has("phone")) res.setContactPhone(tags.path("phone").asText());
                        else if (tags.has("contact:phone")) res.setContactPhone(tags.path("contact:phone").asText());

                        if (tags.has("website")) res.setWebsiteUrl(tags.path("website").asText());
                        else if (tags.has("contact:website")) res.setWebsiteUrl(tags.path("contact:website").asText());

                        results.add(res);
                    }
                }
            } else {
                log.warn("Overpass API returned status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.warn("Overpass POI query failed for ({}, {}): {}", latitude, longitude, ex.getMessage());
        }

        // Sort by distance
        results.sort(Comparator.comparing(ExternalDiscoveryResult::getDistanceKm));

        nearbyCache.put(cacheKey, new CacheEntry<>(results, System.currentTimeMillis() + CACHE_TTL_MS));
        return results;
    }

    @Override
    public String getProviderName() {
        return "OpenStreetMap";
    }

    @Override
    public String getAttribution() {
        return "Map data © OpenStreetMap contributors (ODbL)";
    }

    @Override
    public boolean isAvailable() {
        return enabled;
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

    private record CacheEntry<T>(T value, long expiry) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiry;
        }
    }
}

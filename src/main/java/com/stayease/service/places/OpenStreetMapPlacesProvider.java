package com.stayease.service.places;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayease.dto.Coordinates;
import com.stayease.dto.ExternalDiscoveryResult;
import com.stayease.dto.LocationSuggestion;
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
 * OpenStreetMap implementation of {@link PlacesProvider} using Nominatim & Photon for geocoding / autocomplete,
 * and Overpass API + bounded Nominatim POI discovery for nearby hostel / PG / accommodation discovery worldwide.
 * Free, non-commercial/fair-use compliant, with server-side caching and graceful degradation.
 */
@Component
public class OpenStreetMapPlacesProvider implements PlacesProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenStreetMapPlacesProvider.class);

    private static final String USER_AGENT = "StayEase-Global-Discovery/1.0 (https://stayease.local; contact@stayease.local)";
    private static final String NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search";
    private static final String NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse";
    private static final String PHOTON_URL = "https://photon.komoot.io/api/";

    private static final List<String> OVERPASS_ENDPOINTS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    // Lightweight in-memory caches to respect rate limits and deliver snappy typeahead responses
    private final Map<String, CacheEntry<Optional<Coordinates>>> geocodeCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Optional<String>>> reverseGeocodeCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<LocationSuggestion>>> suggestionCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<ExternalDiscoveryResult>>> nearbyCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 600_000L; // 10 minutes

    public OpenStreetMapPlacesProvider(
            ObjectMapper objectMapper,
            @Value("${stayease.places.osm.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
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
            String url = NOMINATIM_SEARCH_URL + "?q=" + encodedQuery + "&format=json&limit=1&addressdetails=1";

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

        // Fallback to Photon geocoding if Nominatim fails
        try {
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            String url = PHOTON_URL + "?q=" + encodedQuery + "&limit=1";

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
                JsonNode features = root.path("features");
                if (features.isArray() && !features.isEmpty()) {
                    JsonNode feat = features.get(0);
                    JsonNode coords = feat.path("geometry").path("coordinates");
                    if (coords.isArray() && coords.size() >= 2) {
                        double lon = coords.get(0).asDouble();
                        double lat = coords.get(1).asDouble();
                        String name = feat.path("properties").path("name").asText(query.trim());
                        String city = feat.path("properties").path("city").asText("");
                        String state = feat.path("properties").path("state").asText("");
                        String country = feat.path("properties").path("country").asText("");
                        String display = name + (!city.isEmpty() ? ", " + city : "") + (!state.isEmpty() ? ", " + state : "") + (!country.isEmpty() ? ", " + country : "");

                        Optional<Coordinates> result = Optional.of(new Coordinates(lat, lon, display));
                        geocodeCache.put(normalizedKey, new CacheEntry<>(result, System.currentTimeMillis() + CACHE_TTL_MS));
                        return result;
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Photon fallback geocode failed for '{}': {}", query, ex.getMessage());
        }

        Optional<Coordinates> empty = Optional.empty();
        geocodeCache.put(normalizedKey, new CacheEntry<>(empty, System.currentTimeMillis() + 60_000L));
        return empty;
    }

    @Override
    public Optional<String> reverseGeocode(double latitude, double longitude) {
        if (!enabled) {
            return Optional.empty();
        }

        String cacheKey = String.format(Locale.ROOT, "%.4f_%.4f", latitude, longitude);
        CacheEntry<Optional<String>> cached = reverseGeocodeCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }

        try {
            String url = String.format(Locale.ROOT,
                    "%s?lat=%.6f&lon=%.6f&format=json&addressdetails=1",
                    NOMINATIM_REVERSE_URL, latitude, longitude);

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
                JsonNode address = root.path("address");
                String displayName = root.path("display_name").asText(null);

                String locality = address.path("suburb").asText("");
                if (locality.isEmpty()) locality = address.path("neighbourhood").asText("");
                if (locality.isEmpty()) locality = address.path("residential").asText("");

                String city = address.path("city").asText("");
                if (city.isEmpty()) city = address.path("town").asText("");
                if (city.isEmpty()) city = address.path("county").asText("");

                String state = address.path("state").asText("");
                String country = address.path("country").asText("");

                StringBuilder sb = new StringBuilder();
                if (!locality.isEmpty()) sb.append(locality).append(", ");
                if (!city.isEmpty()) sb.append(city).append(", ");
                if (!state.isEmpty()) sb.append(state).append(", ");
                if (!country.isEmpty()) sb.append(country);

                String formatted = sb.toString().trim();
                if (formatted.endsWith(",")) formatted = formatted.substring(0, formatted.length() - 1).trim();

                String finalName = !formatted.isEmpty() ? formatted : displayName;
                if (finalName != null && !finalName.trim().isEmpty()) {
                    Optional<String> res = Optional.of(finalName);
                    reverseGeocodeCache.put(cacheKey, new CacheEntry<>(res, System.currentTimeMillis() + CACHE_TTL_MS));
                    return res;
                }
            }
        } catch (Exception ex) {
            log.debug("Nominatim reverse geocode failed for ({}, {}): {}", latitude, longitude, ex.getMessage());
        }

        Optional<String> empty = Optional.empty();
        reverseGeocodeCache.put(cacheKey, new CacheEntry<>(empty, System.currentTimeMillis() + 60_000L));
        return empty;
    }

    @Override
    public List<LocationSuggestion> suggestLocations(String query, int limit) {
        if (!enabled || query == null || query.trim().length() < 2) {
            return Collections.emptyList();
        }

        int max = Math.min(Math.max(limit, 1), 8);
        String normalizedKey = query.trim().toLowerCase();
        CacheEntry<List<LocationSuggestion>> cached = suggestionCache.get(normalizedKey);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }

        List<LocationSuggestion> suggestions = new ArrayList<>();

        // 1. Try fast typeahead via Photon (Komoot OSM)
        try {
            String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            String url = PHOTON_URL + "?q=" + encoded + "&limit=" + max;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode features = root.path("features");
                if (features.isArray()) {
                    for (JsonNode feat : features) {
                        JsonNode props = feat.path("properties");
                        JsonNode geom = feat.path("geometry");
                        JsonNode coords = geom.path("coordinates");
                        if (coords.isArray() && coords.size() >= 2) {
                            double lon = coords.get(0).asDouble();
                            double lat = coords.get(1).asDouble();

                            String name = props.path("name").asText("");
                            String city = props.path("city").asText("");
                            String county = props.path("county").asText("");
                            String state = props.path("state").asText("");
                            String country = props.path("country").asText("");
                            String postcode = props.path("postcode").asText("");
                            String type = props.path("type").asText(props.path("osm_value").asText("place"));

                            String primary = !name.isEmpty() ? name : (!city.isEmpty() ? city : query.trim());

                            StringBuilder secondary = new StringBuilder();
                            if (!city.isEmpty() && !city.equalsIgnoreCase(primary)) secondary.append(city).append(", ");
                            if (!county.isEmpty() && !county.equalsIgnoreCase(primary)) secondary.append(county).append(", ");
                            if (!state.isEmpty()) secondary.append(state).append(", ");
                            if (!postcode.isEmpty()) secondary.append(postcode).append(", ");
                            if (!country.isEmpty()) secondary.append(country);

                            String secText = secondary.toString().trim();
                            if (secText.endsWith(",")) secText = secText.substring(0, secText.length() - 1).trim();

                            String fullDisplay = !secText.isEmpty() ? (primary + ", " + secText) : primary;

                            suggestions.add(new LocationSuggestion(fullDisplay, primary, secText, lat, lon, type));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Photon suggestion failed for '{}': {}", query, ex.getMessage());
        }

        // 2. Fallback to Nominatim search if Photon returned empty or failed
        if (suggestions.isEmpty()) {
            try {
                String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
                String url = NOMINATIM_SEARCH_URL + "?q=" + encoded + "&format=json&limit=" + max + "&addressdetails=1";

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(4))
                        .GET()
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(resp.body());
                    if (root.isArray()) {
                        for (JsonNode item : root) {
                            double lat = Double.parseDouble(item.path("lat").asText());
                            double lon = Double.parseDouble(item.path("lon").asText());
                            String displayName = item.path("display_name").asText();
                            String name = item.path("name").asText("");
                            String type = item.path("type").asText("place");

                            String primary = !name.isEmpty() ? name : query.trim();
                            String secondary = displayName;
                            if (displayName.startsWith(primary + ",")) {
                                secondary = displayName.substring(primary.length() + 1).trim();
                            }

                            suggestions.add(new LocationSuggestion(displayName, primary, secondary, lat, lon, type));
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Nominatim suggestion failed for '{}': {}", query, ex.getMessage());
            }
        }

        suggestionCache.put(normalizedKey, new CacheEntry<>(suggestions, System.currentTimeMillis() + CACHE_TTL_MS));
        return suggestions;
    }

    @Override
    public List<ExternalDiscoveryResult> searchNearbyAccommodations(double latitude, double longitude, int radiusKm) {
        if (!enabled) {
            return Collections.emptyList();
        }

        // Support worldwide radius up to 100km cleanly
        int clampedRadiusKm = Math.min(Math.max(radiusKm, 1), 100);
        String cacheKey = String.format(Locale.ROOT, "%.3f_%.3f_%d", latitude, longitude, clampedRadiusKm);

        CacheEntry<List<ExternalDiscoveryResult>> cached = nearbyCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }

        List<ExternalDiscoveryResult> results = new ArrayList<>();

        // If radius is <= 35km, try fast Overpass query first
        if (clampedRadiusKm <= 35) {
            results = queryOverpassAccommodations(latitude, longitude, clampedRadiusKm);
        }

        // If Overpass returned empty (or failed/timed out, or radius is large > 35km up to 100km),
        // fallback to fast bounded Nominatim POI search across multiple accommodation categories!
        if (results.isEmpty() || clampedRadiusKm > 35) {
            List<ExternalDiscoveryResult> nominatimResults = queryNominatimBoundedAccommodations(latitude, longitude, clampedRadiusKm);
            if (!nominatimResults.isEmpty()) {
                // Merge without duplicates
                Set<String> existingNames = new HashSet<>();
                for (ExternalDiscoveryResult r : results) {
                    existingNames.add(r.getName().trim().toLowerCase());
                }
                for (ExternalDiscoveryResult nr : nominatimResults) {
                    if (existingNames.add(nr.getName().trim().toLowerCase())) {
                        results.add(nr);
                    }
                }
            }
        }

        // If still empty (e.g. rural area on Nominatim), try Overpass as secondary fallback
        if (results.isEmpty() && clampedRadiusKm > 35) {
            results = queryOverpassAccommodations(latitude, longitude, clampedRadiusKm);
        }

        // Sort strictly by distance ascending
        results.sort(Comparator.comparing(ExternalDiscoveryResult::getDistanceKm));

        nearbyCache.put(cacheKey, new CacheEntry<>(results, System.currentTimeMillis() + CACHE_TTL_MS));
        return results;
    }

    private List<ExternalDiscoveryResult> queryOverpassAccommodations(double latitude, double longitude, int radiusKm) {
        int radiusMeters = radiusKm * 1000;
        // Optimized regex Overpass QL query covering Hotels, Hostels, PGs, Guest Houses, Motels, and Student Accommodations
        String overpassQuery = String.format(Locale.ROOT,
                "[out:json][timeout:5];(" +
                "node[\"tourism\"~\"^(hotel|hostel|guest_house|motel|apartment)$\"](around:%d,%.6f,%.6f);" +
                "node[\"amenity\"~\"^(student_accommodation|dormitory)$\"](around:%d,%.6f,%.6f);" +
                "way[\"tourism\"~\"^(hotel|hostel|guest_house|motel)$\"](around:%d,%.6f,%.6f);" +
                ");out center 50;",
                radiusMeters, latitude, longitude,
                radiusMeters, latitude, longitude,
                radiusMeters, latitude, longitude
        );

        List<ExternalDiscoveryResult> results = new ArrayList<>();
        String requestBody = "data=" + URLEncoder.encode(overpassQuery, StandardCharsets.UTF_8);

        // Try primary and secondary Overpass endpoints with fallback
        for (String endpoint : OVERPASS_ENDPOINTS) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("User-Agent", USER_AGENT)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .timeout(Duration.ofSeconds(4))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
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
                                continue;
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

                            String tourismTag = tags.path("tourism").asText("");
                            String amenityTag = tags.path("amenity").asText("");

                            String category = "Hotel";
                            if ("hotel".equalsIgnoreCase(tourismTag)) {
                                category = "Hotel";
                            } else if ("hostel".equalsIgnoreCase(tourismTag)) {
                                category = "Hostel / Backpacker";
                            } else if ("guest_house".equalsIgnoreCase(tourismTag)) {
                                category = "Guest House / Homestay";
                            } else if ("motel".equalsIgnoreCase(tourismTag)) {
                                category = "Motel";
                            } else if ("apartment".equalsIgnoreCase(tourismTag)) {
                                category = "Apartment / PG";
                            } else if ("student_accommodation".equalsIgnoreCase(amenityTag)) {
                                category = "Student PG / Accommodation";
                            } else if ("dormitory".equalsIgnoreCase(amenityTag)) {
                                category = "PG / Dormitory";
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
                                String suburb = tags.path("addr:suburb").asText("");
                                finalAddress = !suburb.isEmpty() ? suburb : "Registered local accommodation near coordinates";
                            }

                            double dist = calculateHaversineKm(latitude, longitude, itemLat, itemLon);

                            String googleMapsDirectionsUrl = String.format(Locale.ROOT,
                                    "https://www.google.com/maps/dir/?api=1&destination=%.6f%%2C%.6f",
                                    itemLat, itemLon);

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
                                    googleMapsDirectionsUrl
                            );

                            if (tags.has("phone")) res.setContactPhone(tags.path("phone").asText());
                            else if (tags.has("contact:phone")) res.setContactPhone(tags.path("contact:phone").asText());

                            if (tags.has("website")) res.setWebsiteUrl(tags.path("website").asText());
                            else if (tags.has("contact:website")) res.setWebsiteUrl(tags.path("contact:website").asText());

                            enrichResultCard(res, category);
                            results.add(res);
                        }
                    }
                    break;
                } else {
                    log.warn("Overpass endpoint {} returned status {}. Trying next mirror if available.", endpoint, response.statusCode());
                }
            } catch (Exception ex) {
                log.warn("Overpass query failed on endpoint {}: {}", endpoint, ex.getMessage());
            }
        }
        return results;
    }

    private List<ExternalDiscoveryResult> queryNominatimBoundedAccommodations(double latitude, double longitude, int radiusKm) {
        List<ExternalDiscoveryResult> list = new ArrayList<>();
        double deltaLat = radiusKm / 111.0;
        double deltaLon = radiusKm / (111.0 * Math.max(Math.cos(Math.toRadians(latitude)), 0.1));
        double minLat = latitude - deltaLat;
        double maxLat = latitude + deltaLat;
        double minLon = longitude - deltaLon;
        double maxLon = longitude + deltaLon;

        // Query hotels, hostels, and guest houses within the bounding box
        List<String> categoriesToQuery = List.of("hotel", "hostel", "guest house");
        for (String term : categoriesToQuery) {
            try {
                String encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8);
                String viewbox = String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f", minLon, maxLat, maxLon, minLat);
                String url = NOMINATIM_SEARCH_URL + "?q=" + encodedTerm + "&format=json&limit=15&viewbox=" + viewbox + "&bounded=1&addressdetails=1";

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
                    if (root.isArray()) {
                        for (JsonNode el : root) {
                            String name = el.path("name").asText(null);
                            if (name == null || name.trim().isEmpty()) {
                                name = el.path("display_name").asText("Accommodation");
                                if (name.contains(",")) name = name.substring(0, name.indexOf(',')).trim();
                            }
                            if (name.equalsIgnoreCase("hotel") || name.equalsIgnoreCase("hostel") || name.equalsIgnoreCase("guest house")) {
                                continue;
                            }

                            double itemLat = Double.parseDouble(el.path("lat").asText());
                            double itemLon = Double.parseDouble(el.path("lon").asText());
                            double dist = calculateHaversineKm(latitude, longitude, itemLat, itemLon);
                            if (dist > radiusKm) {
                                continue;
                            }

                            String type = el.path("type").asText("hotel");
                            String category = "Hotel";
                            if ("hostel".equalsIgnoreCase(type) || term.contains("hostel") || name.toLowerCase().contains("hostel")) {
                                category = "Hostel / Backpacker";
                            } else if ("guest_house".equalsIgnoreCase(type) || term.contains("guest") || name.toLowerCase().contains("guest")) {
                                category = "Guest House / Homestay";
                            } else if (name.toLowerCase().contains("pg") || name.toLowerCase().contains("paying guest")) {
                                category = "Student PG / Accommodation";
                            }

                            long osmId = el.path("osm_id").asLong(System.currentTimeMillis());
                            String extId = "osm-" + el.path("osm_type").asText("node") + "-" + osmId;
                            String displayName = el.path("display_name").asText("Nearby accommodation");

                            String googleMapsDirectionsUrl = String.format(Locale.ROOT,
                                    "https://www.google.com/maps/dir/?api=1&destination=%.6f%%2C%.6f",
                                    itemLat, itemLon);

                            ExternalDiscoveryResult res = new ExternalDiscoveryResult(
                                    extId,
                                    name,
                                    category,
                                    displayName,
                                    "Local Area",
                                    "Region",
                                    itemLat,
                                    itemLon,
                                    dist,
                                    "OpenStreetMap",
                                    "© OpenStreetMap contributors",
                                    googleMapsDirectionsUrl
                            );
                            enrichResultCard(res, category);
                            list.add(res);
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Nominatim bounded search for '{}' failed: {}", term, ex.getMessage());
            }
        }
        return list;
    }

    private void enrichResultCard(ExternalDiscoveryResult res, String category) {
        if (category.contains("Hotel") || category.contains("Motel")) {
            res.setImagePath("/images/hero_room.jpg");
            res.setPricePerNight(new java.math.BigDecimal("1850.00"));
            res.setAmenities(java.util.List.of("Free High-Speed Wi-Fi", "Air Conditioning", "Ensuite Bathroom", "Housekeeping", "24/7 Desk"));
        } else if (category.contains("Hostel")) {
            res.setImagePath("/images/dormitory_room.jpg");
            res.setPricePerNight(new java.math.BigDecimal("650.00"));
            res.setMonthlyRent(new java.math.BigDecimal("7500.00"));
            res.setAmenities(java.util.List.of("High-Speed Wi-Fi", "Bunk Bed & Locker", "Shared Kitchen", "Study Area", "CCTV Security"));
        } else if (category.contains("PG") || category.contains("Student") || category.contains("Dormitory")) {
            res.setImagePath("/images/deluxe_room.jpg");
            res.setPricePerNight(new java.math.BigDecimal("550.00"));
            res.setMonthlyRent(new java.math.BigDecimal("6800.00"));
            res.setAmenities(java.util.List.of("Wi-Fi Included", "Meal Options", "Laundry Service", "Study Table", "Power Backup"));
        } else {
            res.setImagePath("/images/deluxe_room.jpg");
            res.setPricePerNight(new java.math.BigDecimal("1250.00"));
            res.setAmenities(java.util.List.of("Free Wi-Fi", "Air Conditioning", "Ensuite Bathroom", "Balcony", "Kitchenette"));
        }
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

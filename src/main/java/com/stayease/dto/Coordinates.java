package com.stayease.dto;

import java.io.Serializable;

/**
 * Record holding latitude, longitude, and an optional resolved geographic display name.
 */
public record Coordinates(Double latitude, Double longitude, String displayName) implements Serializable {

    public boolean isValid() {
        return latitude != null && longitude != null
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }
}

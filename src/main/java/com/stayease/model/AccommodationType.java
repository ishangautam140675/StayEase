package com.stayease.model;

/**
 * Type of accommodation represented in the StayEase discovery system.
 */
public enum AccommodationType {
    HOSTEL("Hostel"),
    PG("Paying Guest (PG)"),
    COLIVING("Co-Living Space"),
    APARTMENT("Apartment / Flat"),
    HOTEL("Hotel / Guest House");

    private final String displayName;

    AccommodationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

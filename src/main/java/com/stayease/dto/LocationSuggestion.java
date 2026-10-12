package com.stayease.dto;

/**
 * Data Transfer Object representing an autocomplete/typeahead location suggestion.
 * Compatible with Google Maps / Uber-style interactive location searching.
 */
public class LocationSuggestion {

    private String displayName;
    private String primaryName;
    private String secondaryText;
    private Double latitude;
    private Double longitude;
    private String placeType;

    public LocationSuggestion() {
    }

    public LocationSuggestion(String displayName, String primaryName, String secondaryText, Double latitude, Double longitude, String placeType) {
        this.displayName = displayName;
        this.primaryName = primaryName;
        this.secondaryText = secondaryText;
        this.latitude = latitude;
        this.longitude = longitude;
        this.placeType = placeType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPrimaryName() {
        return primaryName;
    }

    public void setPrimaryName(String primaryName) {
        this.primaryName = primaryName;
    }

    public String getSecondaryText() {
        return secondaryText;
    }

    public void setSecondaryText(String secondaryText) {
        this.secondaryText = secondaryText;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getPlaceType() {
        return placeType;
    }

    public void setPlaceType(String placeType) {
        this.placeType = placeType;
    }
}

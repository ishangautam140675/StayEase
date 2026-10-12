package com.stayease.dto;

import java.io.Serializable;

/**
 * Normalized DTO representing an external accommodation business found via external location providers
 * (e.g. OpenStreetMap). External businesses are strictly non-bookable through StayEase.
 */
public class ExternalDiscoveryResult implements Serializable {

    private String externalId;
    private String name;
    private String accommodationType;
    private String address;
    private String locality;
    private String city;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
    private String providerSource;
    private String attributionText;
    private String directionsUrl;
    private String websiteUrl;
    private String contactPhone;
    private boolean bookableOnStayEase = false;
    private java.math.BigDecimal pricePerNight;
    private java.math.BigDecimal monthlyRent;
    private String imagePath;
    private java.util.List<String> amenities;

    public ExternalDiscoveryResult() {
    }

    public ExternalDiscoveryResult(String externalId, String name, String accommodationType,
                                   String address, String locality, String city,
                                   Double latitude, Double longitude, Double distanceKm,
                                   String providerSource, String attributionText, String directionsUrl) {
        this.externalId = externalId;
        this.name = name;
        this.accommodationType = accommodationType;
        this.address = address;
        this.locality = locality;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceKm = distanceKm;
        this.providerSource = providerSource;
        this.attributionText = attributionText;
        this.directionsUrl = directionsUrl;
        this.bookableOnStayEase = false;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccommodationType() {
        return accommodationType;
    }

    public void setAccommodationType(String accommodationType) {
        this.accommodationType = accommodationType;
    }

    public String getAddress() {
        return address != null && !address.trim().isEmpty() ? address : "Address not provided";
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
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

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public String getProviderSource() {
        return providerSource;
    }

    public void setProviderSource(String providerSource) {
        this.providerSource = providerSource;
    }

    public String getAttributionText() {
        return attributionText;
    }

    public void setAttributionText(String attributionText) {
        this.attributionText = attributionText;
    }

    public String getDirectionsUrl() {
        if (directionsUrl != null) {
            return directionsUrl;
        }
        if (latitude != null && longitude != null) {
            return "https://www.google.com/maps/dir/?api=1&destination=" + latitude + "%2C" + longitude;
        }
        return null;
    }

    public String getOsmDirectionsUrl() {
        if (latitude != null && longitude != null) {
            return "https://www.openstreetmap.org/directions?to=" + latitude + "%2C" + longitude;
        }
        return null;
    }

    public void setDirectionsUrl(String directionsUrl) {
        this.directionsUrl = directionsUrl;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public String getContactPhone() {
        return contactPhone != null && !contactPhone.trim().isEmpty() ? contactPhone : "Not provided";
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public boolean isBookableOnStayEase() {
        return bookableOnStayEase;
    }

    public void setBookableOnStayEase(boolean bookableOnStayEase) {
        this.bookableOnStayEase = bookableOnStayEase;
    }

    public java.math.BigDecimal getPricePerNight() {
        return pricePerNight != null ? pricePerNight : new java.math.BigDecimal("750.00");
    }

    public void setPricePerNight(java.math.BigDecimal pricePerNight) {
        this.pricePerNight = pricePerNight;
    }

    public java.math.BigDecimal getMonthlyRent() {
        return monthlyRent;
    }

    public void setMonthlyRent(java.math.BigDecimal monthlyRent) {
        this.monthlyRent = monthlyRent;
    }

    public String getImagePath() {
        return imagePath != null ? imagePath : "/images/hero_room.jpg";
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public java.util.List<String> getAmenities() {
        return amenities != null ? amenities : java.util.List.of("Free Wi-Fi", "Air Conditioning", "Ensuite Bath");
    }

    public void setAmenities(java.util.List<String> amenities) {
        this.amenities = amenities;
    }
}

package com.stayease.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Entity representing a room available for booking in the StayEase system.
 */
@Entity
@Table(name = "rooms", indexes = {
    @Index(name = "idx_rooms_catalog", columnList = "active, under_maintenance, type, capacity")
})
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_number", nullable = false, unique = true, length = 10)
    private String roomNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private RoomType type;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "price_per_night", precision = 10, scale = 2, nullable = false)
    private BigDecimal pricePerNight;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "under_maintenance", nullable = false)
    private boolean underMaintenance = false;

    @Column(name = "amenities", length = 500)
    private String amenities;

    @Column(name = "property_name", length = 150)
    private String propertyName = "StayEase Premier Living";

    @Enumerated(EnumType.STRING)
    @Column(name = "accommodation_type", length = 30)
    private AccommodationType accommodationType = AccommodationType.HOSTEL;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "locality", length = 100)
    private String locality;

    @Column(name = "city", length = 100)
    private String city = "Bengaluru";

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "monthly_rent", precision = 10, scale = 2)
    private BigDecimal monthlyRent;

    @Column(name = "verified", nullable = false)
    private boolean verified = true;

    @Column(name = "last_verified_at")
    private java.time.LocalDateTime lastVerifiedAt;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    public Room() {
    }

    public Room(String roomNumber, RoomType type, int capacity, BigDecimal pricePerNight, String description, boolean active) {
        this(roomNumber, type, capacity, pricePerNight, description, active, false, null);
    }

    public Room(String roomNumber, RoomType type, int capacity, BigDecimal pricePerNight, String description, boolean active, boolean underMaintenance, String amenities) {
        this.roomNumber = roomNumber;
        this.type = type;
        this.capacity = capacity;
        this.pricePerNight = pricePerNight;
        this.description = description;
        this.active = active;
        this.underMaintenance = underMaintenance;
        this.amenities = amenities;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public RoomType getType() {
        return type;
    }

    public void setType(RoomType type) {
        this.type = type;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public void setPricePerNight(BigDecimal pricePerNight) {
        this.pricePerNight = pricePerNight;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isUnderMaintenance() {
        return underMaintenance;
    }

    public void setUnderMaintenance(boolean underMaintenance) {
        this.underMaintenance = underMaintenance;
    }

    public String getAmenities() {
        return amenities;
    }

    public void setAmenities(String amenities) {
        this.amenities = amenities;
    }

    public java.util.List<String> getAmenityList() {
        if (amenities == null || amenities.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.stream(amenities.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public boolean hasAmenity(String amenity) {
        if (amenity == null || amenity.trim().isEmpty() || amenities == null) {
            return false;
        }
        return getAmenityList().stream()
                .anyMatch(a -> a.equalsIgnoreCase(amenity.trim()));
    }

    public String getImagePath() {
        if (photoUrl != null && !photoUrl.trim().isEmpty()) {
            return photoUrl;
        }
        if (type == RoomType.DORMITORY) {
            return "/images/dormitory_room.jpg";
        }
        if (type == RoomType.DOUBLE || type == RoomType.TRIPLE) {
            return "/images/deluxe_room.jpg";
        }
        return "/images/hero_room.jpg";
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public AccommodationType getAccommodationType() {
        return accommodationType;
    }

    public void setAccommodationType(AccommodationType accommodationType) {
        this.accommodationType = accommodationType;
    }

    public String getAddress() {
        return address;
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

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
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

    public BigDecimal getMonthlyRent() {
        return monthlyRent;
    }

    public void setMonthlyRent(BigDecimal monthlyRent) {
        this.monthlyRent = monthlyRent;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public java.time.LocalDateTime getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(java.time.LocalDateTime lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public boolean isLocationAvailable() {
        return latitude != null && longitude != null;
    }

    /**
     * Calculates distance to this property from a given user latitude and longitude in kilometers
     * using the Haversine formula. Returns null if property coordinates are missing.
     */
    public Double calculateDistanceKm(Double userLat, Double userLng) {
        if (userLat == null || userLng == null || latitude == null || longitude == null) {
            return null;
        }
        final double R = 6371.0; // Earth radius in km
        double latDistance = Math.toRadians(latitude - userLat);
        double lonDistance = Math.toRadians(longitude - userLng);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(latitude))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(R * c * 100.0) / 100.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Room room)) return false;
        return id != null && Objects.equals(id, room.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

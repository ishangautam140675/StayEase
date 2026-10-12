package com.stayease.dto;

import com.stayease.model.Room;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO representing an authentic, admin-managed StayEase property listing with distance calculations.
 */
public class StayEaseListingItem implements Serializable {

    private Long id;
    private String roomNumber;
    private String propertyName;
    private String accommodationType;
    private String address;
    private String locality;
    private String city;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
    private BigDecimal pricePerNight;
    private BigDecimal monthlyRent;
    private int capacity;
    private String type;
    private String description;
    private String imagePath;
    private List<String> amenities;
    private boolean verified;
    private boolean bookable = true;

    public StayEaseListingItem() {
    }

    public static StayEaseListingItem fromRoom(Room room, Double userLat, Double userLng) {
        StayEaseListingItem item = new StayEaseListingItem();
        item.setId(room.getId());
        item.setRoomNumber(room.getRoomNumber());
        item.setPropertyName(room.getPropertyName() != null ? room.getPropertyName() : "StayEase Living");
        item.setAccommodationType(room.getAccommodationType() != null ? room.getAccommodationType().getDisplayName() : "Hostel");
        item.setAddress(room.getAddress());
        item.setLocality(room.getLocality());
        item.setCity(room.getCity() != null ? room.getCity() : "Bengaluru");
        item.setLatitude(room.getLatitude());
        item.setLongitude(room.getLongitude());
        item.setPricePerNight(room.getPricePerNight());
        item.setMonthlyRent(room.getMonthlyRent());
        item.setCapacity(room.getCapacity());
        item.setType(room.getType() != null ? room.getType().name() : "SINGLE");
        item.setDescription(room.getDescription());
        item.setImagePath(room.getImagePath());
        item.setAmenities(room.getAmenityList());
        item.setVerified(room.isVerified());
        item.setBookable(room.isActive() && !room.isUnderMaintenance());

        if (userLat != null && userLng != null && room.isLocationAvailable()) {
            item.setDistanceKm(room.calculateDistanceKm(userLat, userLng));
        }

        return item;
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

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getAccommodationType() {
        return accommodationType;
    }

    public void setAccommodationType(String accommodationType) {
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

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public void setPricePerNight(BigDecimal pricePerNight) {
        this.pricePerNight = pricePerNight;
    }

    public BigDecimal getMonthlyRent() {
        return monthlyRent;
    }

    public void setMonthlyRent(BigDecimal monthlyRent) {
        this.monthlyRent = monthlyRent;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public List<String> getAmenities() {
        return amenities;
    }

    public void setAmenities(List<String> amenities) {
        this.amenities = amenities;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public boolean isBookable() {
        return bookable;
    }

    public void setBookable(boolean bookable) {
        this.bookable = bookable;
    }
}

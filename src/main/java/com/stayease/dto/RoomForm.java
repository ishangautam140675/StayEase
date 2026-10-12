package com.stayease.dto;

import com.stayease.model.Room;
import com.stayease.model.RoomType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Form backing object for creating and editing accommodations.
 */
public class RoomForm {

    @NotBlank(message = "Room number is required")
    @Size(max = 10, message = "Room number must not exceed 10 characters")
    private String roomNumber;

    @NotNull(message = "Room type is required")
    private RoomType type;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 10, message = "Capacity cannot exceed 10")
    private Integer capacity = 1;

    @NotNull(message = "Price per night is required")
    @DecimalMin(value = "1.00", message = "Price per night must be at least 1.00")
    private BigDecimal pricePerNight;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    private boolean active = true;

    private boolean underMaintenance = false;

    @Size(max = 500, message = "Amenities must not exceed 500 characters")
    private String amenities;

    @Size(max = 150, message = "Property name must not exceed 150 characters")
    private String propertyName = "StayEase Premier Living";

    private com.stayease.model.AccommodationType accommodationType = com.stayease.model.AccommodationType.HOSTEL;

    @Size(max = 255, message = "Address must not exceed 255 characters")
    private String address;

    @Size(max = 100, message = "Locality must not exceed 100 characters")
    private String locality;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city = "Bengaluru";

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;

    private Double latitude;

    private Double longitude;

    @DecimalMin(value = "0.00", message = "Monthly rent must be positive or zero")
    private BigDecimal monthlyRent;

    private boolean verified = true;

    @Size(max = 500, message = "Photo URL must not exceed 500 characters")
    private String photoUrl;

    public RoomForm() {
    }

    public RoomForm(String roomNumber, RoomType type, Integer capacity, BigDecimal pricePerNight, String description, boolean active) {
        this(roomNumber, type, capacity, pricePerNight, description, active, false, null);
    }

    public RoomForm(String roomNumber, RoomType type, Integer capacity, BigDecimal pricePerNight, String description, boolean active, boolean underMaintenance, String amenities) {
        this.roomNumber = roomNumber;
        this.type = type;
        this.capacity = capacity;
        this.pricePerNight = pricePerNight;
        this.description = description;
        this.active = active;
        this.underMaintenance = underMaintenance;
        this.amenities = amenities;
    }

    public static RoomForm from(Room room) {
        RoomForm form = new RoomForm(
                room.getRoomNumber(),
                room.getType(),
                room.getCapacity(),
                room.getPricePerNight(),
                room.getDescription(),
                room.isActive(),
                room.isUnderMaintenance(),
                room.getAmenities()
        );
        form.setPropertyName(room.getPropertyName());
        form.setAccommodationType(room.getAccommodationType());
        form.setAddress(room.getAddress());
        form.setLocality(room.getLocality());
        form.setCity(room.getCity());
        form.setPostalCode(room.getPostalCode());
        form.setLatitude(room.getLatitude());
        form.setLongitude(room.getLongitude());
        form.setMonthlyRent(room.getMonthlyRent());
        form.setVerified(room.isVerified());
        form.setPhotoUrl(room.getPhotoUrl());
        return form;
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

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
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

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public com.stayease.model.AccommodationType getAccommodationType() {
        return accommodationType;
    }

    public void setAccommodationType(com.stayease.model.AccommodationType accommodationType) {
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

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
}

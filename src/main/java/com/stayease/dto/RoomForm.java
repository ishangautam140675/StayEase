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
        return new RoomForm(
                room.getRoomNumber(),
                room.getType(),
                room.getCapacity(),
                room.getPricePerNight(),
                room.getDescription(),
                room.isActive(),
                room.isUnderMaintenance(),
                room.getAmenities()
        );
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
}

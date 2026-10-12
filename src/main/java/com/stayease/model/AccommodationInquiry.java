package com.stayease.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing an accommodation inquiry or reservation request
 * made from the worldwide discovery portal.
 */
@Entity
@Table(name = "accommodation_inquiries")
public class AccommodationInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String hotelName;

    @Column(nullable = false)
    private String accommodationType;

    @Column(length = 1000)
    private String hotelAddress;

    private Double estimatedPrice;

    @Column(nullable = false)
    private LocalDate checkIn;

    @Column(nullable = false)
    private LocalDate checkOut;

    @Column(nullable = false)
    private String guests;

    @Column(nullable = false)
    private String contactPhone;

    private String guestEmail;

    private String guestName;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, CONTACTED, RESOLVED

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AccommodationInquiry() {
    }

    public AccommodationInquiry(String hotelName, String accommodationType, String hotelAddress,
                                Double estimatedPrice, LocalDate checkIn, LocalDate checkOut,
                                String guests, String contactPhone, String guestEmail,
                                String guestName, String notes) {
        this.hotelName = hotelName;
        this.accommodationType = accommodationType;
        this.hotelAddress = hotelAddress;
        this.estimatedPrice = estimatedPrice;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.guests = guests;
        this.contactPhone = contactPhone;
        this.guestEmail = guestEmail;
        this.guestName = guestName;
        this.notes = notes;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHotelName() {
        return hotelName;
    }

    public void setHotelName(String hotelName) {
        this.hotelName = hotelName;
    }

    public String getAccommodationType() {
        return accommodationType;
    }

    public void setAccommodationType(String accommodationType) {
        this.accommodationType = accommodationType;
    }

    public String getHotelAddress() {
        return hotelAddress;
    }

    public void setHotelAddress(String hotelAddress) {
        this.hotelAddress = hotelAddress;
    }

    public Double getEstimatedPrice() {
        return estimatedPrice;
    }

    public void setEstimatedPrice(Double estimatedPrice) {
        this.estimatedPrice = estimatedPrice;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(LocalDate checkIn) {
        this.checkIn = checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(LocalDate checkOut) {
        this.checkOut = checkOut;
    }

    public String getGuests() {
        return guests;
    }

    public void setGuests(String guests) {
        this.guests = guests;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getGuestEmail() {
        return guestEmail;
    }

    public void setGuestEmail(String guestEmail) {
        this.guestEmail = guestEmail;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

package com.stayease.dto;

import java.time.LocalDate;

/**
 * DTO representing an inquiry submission from the frontend.
 */
public class AccommodationInquiryRequest {

    private String hotelName;
    private String hotelType;
    private String hotelAddress;
    private Double hotelPrice;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private String guests;
    private String phone;
    private String notes;
    private String guestName;

    public AccommodationInquiryRequest() {
    }

    public String getHotelName() {
        return hotelName;
    }

    public void setHotelName(String hotelName) {
        this.hotelName = hotelName;
    }

    public String getHotelType() {
        return hotelType;
    }

    public void setHotelType(String hotelType) {
        this.hotelType = hotelType;
    }

    public String getHotelAddress() {
        return hotelAddress;
    }

    public void setHotelAddress(String hotelAddress) {
        this.hotelAddress = hotelAddress;
    }

    public Double getHotelPrice() {
        return hotelPrice;
    }

    public void setHotelPrice(Double hotelPrice) {
        this.hotelPrice = hotelPrice;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }
}

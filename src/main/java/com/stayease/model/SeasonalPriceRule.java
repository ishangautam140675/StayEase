package com.stayease.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing configurable seasonal pricing overrides.
 * Allows applying a custom nightly rate or rate multiplier to a specific room or room type
 * across a defined date window.
 * Preserves historical booking rates because existing reservations retain their pricing.
 */
@Entity
@Table(name = "seasonal_price_rules", indexes = {
    @Index(name = "idx_seasonal_dates", columnList = "start_date, end_date"),
    @Index(name = "idx_seasonal_room", columnList = "room_id"),
    @Index(name = "idx_seasonal_room_type", columnList = "room_type")
})
public class SeasonalPriceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 120)
    private String ruleName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type")
    private RoomType roomType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Absolute price per night during this season (if set).
     */
    @Column(name = "price_per_night", precision = 10, scale = 2)
    private BigDecimal pricePerNight;

    /**
     * Multiplier applied to standard rate (e.g. 1.25 for +25% peak season), if pricePerNight is not set.
     */
    @Column(name = "price_multiplier", precision = 5, scale = 2)
    private BigDecimal priceMultiplier;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SeasonalPriceRule() {
    }

    public SeasonalPriceRule(String ruleName, Room room, RoomType roomType, LocalDate startDate, LocalDate endDate,
                             BigDecimal pricePerNight, BigDecimal priceMultiplier, boolean active, String createdBy) {
        this.ruleName = ruleName;
        this.room = room;
        this.roomType = roomType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.pricePerNight = pricePerNight;
        this.priceMultiplier = priceMultiplier;
        this.active = active;
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(RoomType roomType) {
        this.roomType = roomType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public void setPricePerNight(BigDecimal pricePerNight) {
        this.pricePerNight = pricePerNight;
    }

    public BigDecimal getPriceMultiplier() {
        return priceMultiplier;
    }

    public void setPriceMultiplier(BigDecimal priceMultiplier) {
        this.priceMultiplier = priceMultiplier;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SeasonalPriceRule that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

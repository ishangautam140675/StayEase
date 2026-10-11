package com.stayease.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transparent itemized price breakdown for a booking reservation.
 */
public class PriceBreakdown {

    public record NightlyItem(LocalDate date, BigDecimal rate) {}

    private final long nights;
    private final BigDecimal pricePerNight;
    private final BigDecimal subtotal;
    private final BigDecimal taxesAndFees;
    private final BigDecimal totalPrice;
    private final List<NightlyItem> nightlyItems;

    public PriceBreakdown(long nights, BigDecimal pricePerNight, LocalDate checkIn, LocalDate checkOut) {
        this.nights = Math.max(0, nights);
        this.pricePerNight = pricePerNight != null ? pricePerNight : BigDecimal.ZERO;
        this.subtotal = this.pricePerNight.multiply(BigDecimal.valueOf(this.nights));
        this.taxesAndFees = BigDecimal.ZERO;
        this.totalPrice = this.subtotal.add(this.taxesAndFees);

        List<NightlyItem> items = new ArrayList<>();
        if (checkIn != null && checkOut != null && this.nights > 0) {
            LocalDate current = checkIn;
            while (current.isBefore(checkOut)) {
                items.add(new NightlyItem(current, this.pricePerNight));
                current = current.plusDays(1);
            }
        }
        this.nightlyItems = Collections.unmodifiableList(items);
    }

    public long getNights() {
        return nights;
    }

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTaxesAndFees() {
        return taxesAndFees;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public List<NightlyItem> getNightlyItems() {
        return nightlyItems;
    }
}

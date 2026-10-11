package com.stayease.service;

import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.SeasonalPriceRule;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.SeasonalPriceRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Service managing seasonal pricing rules and rate calculations.
 * Existing reservations preserve their booked nightly rates in historical records.
 */
@Service
@Transactional(readOnly = true)
public class SeasonalPricingService {

    private final SeasonalPriceRuleRepository ruleRepository;
    private final RoomRepository roomRepository;
    private final AuditLogService auditLogService;

    public SeasonalPricingService(SeasonalPriceRuleRepository ruleRepository,
                                  RoomRepository roomRepository,
                                  AuditLogService auditLogService) {
        this.ruleRepository = ruleRepository;
        this.roomRepository = roomRepository;
        this.auditLogService = auditLogService;
    }

    public Page<SeasonalPriceRule> getRules(Pageable pageable) {
        return ruleRepository.findAllWithRoom(pageable);
    }

    /**
     * Calculates the effective nightly rate for a room on a given calendar date.
     * Checks if any active seasonal price rule applies (room-specific first, then room-type-specific).
     * If an override rate is specified, uses that rate.
     * If a multiplier is specified, multiplies the room's base rate by the multiplier.
     * If no rule applies, returns the room's base rate.
     */
    public BigDecimal calculateEffectiveRateForDate(Room room, LocalDate date) {
        if (room == null || room.getPricePerNight() == null) {
            return BigDecimal.ZERO;
        }

        List<SeasonalPriceRule> rules = ruleRepository.findMatchingRulesForDate(room.getId(), room.getType(), date);
        if (!rules.isEmpty()) {
            SeasonalPriceRule rule = rules.get(0);
            if (rule.getPricePerNight() != null) {
                return rule.getPricePerNight();
            } else if (rule.getPriceMultiplier() != null) {
                return room.getPricePerNight().multiply(rule.getPriceMultiplier()).setScale(2, RoundingMode.HALF_UP);
            }
        }

        return room.getPricePerNight();
    }

    /**
     * Calculates average effective nightly rate for a stay across [checkIn, checkOut).
     */
    public BigDecimal calculateStayAverageRate(Room room, LocalDate checkIn, LocalDate checkOut) {
        if (room == null || room.getPricePerNight() == null || checkIn == null || checkOut == null || !checkIn.isBefore(checkOut)) {
            return room != null && room.getPricePerNight() != null ? room.getPricePerNight() : BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        long days = 0;
        LocalDate cur = checkIn;
        while (cur.isBefore(checkOut)) {
            sum = sum.add(calculateEffectiveRateForDate(room, cur));
            days++;
            cur = cur.plusDays(1);
        }

        if (days == 0) {
            return room.getPricePerNight();
        }
        return sum.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    /**
     * Creates a seasonal price rule and audits the change.
     */
    @Transactional
    public SeasonalPriceRule createRule(String ruleName, Long roomId, RoomType roomType, LocalDate startDate,
                                        LocalDate endDate, BigDecimal pricePerNight, BigDecimal multiplier,
                                        String adminEmail) {
        if (ruleName == null || ruleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Rule name is required.");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date are required.");
        }
        if (!startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date.");
        }
        if (pricePerNight == null && multiplier == null) {
            throw new IllegalArgumentException("Either a fixed nightly price or a price multiplier must be provided.");
        }

        Room room = null;
        if (roomId != null) {
            room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new IllegalArgumentException("Room not found with ID: " + roomId));
        }

        SeasonalPriceRule rule = new SeasonalPriceRule(
                ruleName.trim(),
                room,
                roomType,
                startDate,
                endDate,
                pricePerNight,
                multiplier,
                true,
                adminEmail != null ? adminEmail : "ADMIN"
        );
        SeasonalPriceRule saved = ruleRepository.save(rule);

        auditLogService.log(
                adminEmail,
                "SEASONAL_PRICE_CREATED",
                "SEASONAL_RULE",
                String.valueOf(saved.getId()),
                "Created seasonal pricing rule '" + ruleName.trim() + "' (" + startDate + " to " + endDate + ")"
        );

        return saved;
    }

    /**
     * Deletes a seasonal pricing rule and audits the change.
     */
    @Transactional
    public void deleteRule(Long ruleId, String adminEmail) {
        SeasonalPriceRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found with ID: " + ruleId));

        String name = rule.getRuleName();
        ruleRepository.delete(rule);

        auditLogService.log(
                adminEmail,
                "SEASONAL_PRICE_DELETED",
                "SEASONAL_RULE",
                String.valueOf(ruleId),
                "Deleted seasonal pricing rule '" + name + "'"
        );
    }
}

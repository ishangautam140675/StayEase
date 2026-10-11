package com.stayease.repository;

import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.SeasonalPriceRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SeasonalPriceRuleRepository extends JpaRepository<SeasonalPriceRule, Long> {

    @Query(value = "SELECT r FROM SeasonalPriceRule r LEFT JOIN FETCH r.room ORDER BY r.startDate DESC",
           countQuery = "SELECT count(r) FROM SeasonalPriceRule r")
    Page<SeasonalPriceRule> findAllWithRoom(Pageable pageable);

    @Query("SELECT r FROM SeasonalPriceRule r LEFT JOIN FETCH r.room WHERE r.active = true " +
           "AND (r.room.id = :roomId OR (r.room IS NULL AND (r.roomType IS NULL OR r.roomType = :roomType))) " +
           "AND :date >= r.startDate AND :date <= r.endDate " +
           "ORDER BY r.room.id DESC NULLS LAST, r.roomType DESC NULLS LAST, r.id DESC")
    List<SeasonalPriceRule> findMatchingRulesForDate(
            @Param("roomId") Long roomId,
            @Param("roomType") RoomType roomType,
            @Param("date") LocalDate date
    );

    @Query("SELECT r FROM SeasonalPriceRule r LEFT JOIN FETCH r.room WHERE r.active = true " +
           "AND (r.room.id = :roomId OR (r.room IS NULL AND (r.roomType IS NULL OR r.roomType = :roomType))) " +
           "AND :startDate < r.endDate AND :endDate > r.startDate " +
           "ORDER BY r.room.id DESC NULLS LAST, r.roomType DESC NULLS LAST, r.id DESC")
    List<SeasonalPriceRule> findOverlappingActiveRules(
            @Param("roomId") Long roomId,
            @Param("roomType") RoomType roomType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}

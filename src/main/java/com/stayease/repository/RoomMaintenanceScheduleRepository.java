package com.stayease.repository;

import com.stayease.model.Room;
import com.stayease.model.RoomMaintenanceSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RoomMaintenanceScheduleRepository extends JpaRepository<RoomMaintenanceSchedule, Long> {

    @Query("SELECT s FROM RoomMaintenanceSchedule s JOIN FETCH s.room WHERE s.room.id = :roomId ORDER BY s.startDate DESC")
    List<RoomMaintenanceSchedule> findByRoomIdOrderByStartDateDesc(@Param("roomId") Long roomId);

    @Query(value = "SELECT s FROM RoomMaintenanceSchedule s JOIN FETCH s.room ORDER BY s.startDate DESC",
           countQuery = "SELECT count(s) FROM RoomMaintenanceSchedule s")
    Page<RoomMaintenanceSchedule> findAllWithRoom(Pageable pageable);

    @Query("SELECT s FROM RoomMaintenanceSchedule s WHERE s.room.id = :roomId AND :startDate < s.endDate AND :endDate > s.startDate")
    List<RoomMaintenanceSchedule> findOverlapping(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT s FROM RoomMaintenanceSchedule s WHERE s.room.id = :roomId AND :startDate < s.endDate AND :endDate > s.startDate AND s.id <> :excludeId")
    List<RoomMaintenanceSchedule> findOverlapping(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId
    );

    @Query("SELECT s FROM RoomMaintenanceSchedule s WHERE s.room.id = :roomId AND s.startDate <= :date AND :date < s.endDate")
    List<RoomMaintenanceSchedule> findActiveMaintenanceOnDate(@Param("roomId") Long roomId, @Param("date") LocalDate date);

    @Query("SELECT s FROM RoomMaintenanceSchedule s WHERE s.room.id = :roomId AND s.endDate >= :today ORDER BY s.startDate ASC")
    List<RoomMaintenanceSchedule> findUpcomingByRoomId(@Param("roomId") Long roomId, @Param("today") LocalDate today);
}

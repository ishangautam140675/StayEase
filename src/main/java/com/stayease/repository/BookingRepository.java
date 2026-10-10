package com.stayease.repository;

import com.stayease.model.Booking;
import com.stayease.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Booking} entities.
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"room"})
    List<Booking> findByUserOrderByCreatedAtDesc(User user);

    @Query("SELECT b FROM Booking b JOIN FETCH b.room WHERE b.user.email = :email ORDER BY b.createdAt DESC")
    List<Booking> findByUserEmailOrderByCreatedAtDesc(@Param("email") String email);

    @Query("SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.room ORDER BY b.createdAt DESC")
    List<Booking> findAllWithUserAndRoomOrderByCreatedAtDesc();

    @Query("SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.room WHERE b.status = :status ORDER BY b.createdAt DESC")
    List<Booking> findAllWithUserAndRoomByStatusOrderByCreatedAtDesc(@Param("status") com.stayease.model.BookingStatus status);

    @Query("SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.room WHERE b.id = :id")
    java.util.Optional<Booking> findByIdWithUserAndRoom(@Param("id") Long id);

    long countByStatus(com.stayease.model.BookingStatus status);

    @Query("select b from Booking b where b.room.id = :roomId and b.status in :statuses and :checkIn < b.checkOut and :checkOut > b.checkIn")
    List<Booking> findOverlapping(
            @Param("roomId") Long roomId,
            @Param("checkIn") java.time.LocalDate checkIn,
            @Param("checkOut") java.time.LocalDate checkOut,
            @Param("statuses") java.util.Collection<com.stayease.model.BookingStatus> statuses
    );

    @Query("select b from Booking b where b.room.id = :roomId and b.status in :statuses and :checkIn < b.checkOut and :checkOut > b.checkIn and b.id <> :excludeId")
    List<Booking> findOverlapping(
            @Param("roomId") Long roomId,
            @Param("checkIn") java.time.LocalDate checkIn,
            @Param("checkOut") java.time.LocalDate checkOut,
            @Param("statuses") java.util.Collection<com.stayease.model.BookingStatus> statuses,
            @Param("excludeId") Long excludeId
    );

    @Query("select b from Booking b where b.room.id = :roomId and b.status in :statuses and :checkIn < b.checkOut and :checkOut > b.checkIn and b.id <> :excludeId")
    List<Booking> findOverlappingExcluding(
            @Param("roomId") Long roomId,
            @Param("checkIn") java.time.LocalDate checkIn,
            @Param("checkOut") java.time.LocalDate checkOut,
            @Param("statuses") java.util.Collection<com.stayease.model.BookingStatus> statuses,
            @Param("excludeId") Long excludeId
    );

    boolean existsByRoomId(Long roomId);
}

package com.stayease.repository;

import com.stayease.model.Booking;
import com.stayease.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query(value = "SELECT b FROM Booking b JOIN FETCH b.room WHERE b.user.email = :email ORDER BY b.createdAt DESC",
           countQuery = "SELECT count(b) FROM Booking b WHERE b.user.email = :email")
    Page<Booking> findByUserEmailOrderByCreatedAtDesc(@Param("email") String email, Pageable pageable);

    @Query("SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.room ORDER BY b.createdAt DESC")
    List<Booking> findAllWithUserAndRoomOrderByCreatedAtDesc();

    @Query(value = "SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.room ORDER BY b.createdAt DESC",
           countQuery = "SELECT count(b) FROM Booking b")
    Page<Booking> findAllWithUserAndRoomOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.room WHERE b.status = :status ORDER BY b.createdAt DESC")
    List<Booking> findAllWithUserAndRoomByStatusOrderByCreatedAtDesc(@Param("status") com.stayease.model.BookingStatus status);

    @Query(value = "SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.room WHERE b.status = :status ORDER BY b.createdAt DESC",
           countQuery = "SELECT count(b) FROM Booking b WHERE b.status = :status")
    Page<Booking> findAllWithUserAndRoomByStatusOrderByCreatedAtDesc(@Param("status") com.stayease.model.BookingStatus status, Pageable pageable);

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


    boolean existsByRoomId(Long roomId);

    @Query("SELECT COUNT(DISTINCT b.room.id) FROM Booking b " +
           "WHERE b.room.active = true " +
           "AND b.status = com.stayease.model.BookingStatus.APPROVED " +
           "AND b.checkIn <= :today AND :today < b.checkOut")
    long countOccupiedRoomsOnDate(@Param("today") java.time.LocalDate today);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.createdAt >= :start AND b.createdAt < :end")
    long countByCreatedAtBetween(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.room WHERE b.status = :status ORDER BY b.createdAt DESC")
    List<Booking> findRecentByStatus(@Param("status") com.stayease.model.BookingStatus status, org.springframework.data.domain.Pageable pageable);
}

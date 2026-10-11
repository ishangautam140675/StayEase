package com.stayease.repository;

import com.stayease.model.Room;
import com.stayease.model.RoomType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Room} entities.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query("SELECT r FROM Room r WHERE r.active = true " +
           "AND (:type IS NULL OR r.type = :type) " +
           "AND (:capacity IS NULL OR r.capacity >= :capacity) " +
           "AND (:minPrice IS NULL OR r.pricePerNight >= :minPrice) " +
           "AND (:maxPrice IS NULL OR r.pricePerNight <= :maxPrice) " +
           "ORDER BY r.roomNumber")
    List<Room> searchActiveRooms(
            @Param("type") RoomType type,
            @Param("capacity") Integer capacity,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice
    );

    @Query(value = "SELECT r FROM Room r WHERE r.active = true " +
           "AND (:type IS NULL OR r.type = :type) " +
           "AND (:capacity IS NULL OR r.capacity >= :capacity) " +
           "AND (:minPrice IS NULL OR r.pricePerNight >= :minPrice) " +
           "AND (:maxPrice IS NULL OR r.pricePerNight <= :maxPrice) " +
           "ORDER BY r.roomNumber",
           countQuery = "SELECT count(r) FROM Room r WHERE r.active = true " +
           "AND (:type IS NULL OR r.type = :type) " +
           "AND (:capacity IS NULL OR r.capacity >= :capacity) " +
           "AND (:minPrice IS NULL OR r.pricePerNight >= :minPrice) " +
           "AND (:maxPrice IS NULL OR r.pricePerNight <= :maxPrice)")
    Page<Room> searchActiveRooms(
            @Param("type") RoomType type,
            @Param("capacity") Integer capacity,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT r FROM Room r WHERE r.active = true AND r.underMaintenance = false " +
           "AND r.capacity >= :guests " +
           "AND NOT EXISTS (" +
           "    SELECT b FROM Booking b " +
           "    WHERE b.room = r " +
           "    AND b.status IN :statuses " +
           "    AND :checkIn < b.checkOut " +
           "    AND :checkOut > b.checkIn" +
           ") " +
           "ORDER BY r.roomNumber")
    List<Room> findAvailableRooms(
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("guests") Integer guests,
            @Param("statuses") Collection<com.stayease.model.BookingStatus> statuses
    );

    @Query("SELECT r FROM Room r WHERE r.active = true AND r.underMaintenance = false " +
           "AND (:type IS NULL OR r.type = :type) " +
           "AND (:capacity IS NULL OR r.capacity >= :capacity) " +
           "AND (:minPrice IS NULL OR r.pricePerNight >= :minPrice) " +
           "AND (:maxPrice IS NULL OR r.pricePerNight <= :maxPrice) " +
           "AND (:amenity IS NULL OR LOWER(r.amenities) LIKE LOWER(CONCAT('%', :amenity, '%'))) " +
           "AND (:checkIn IS NULL OR :checkOut IS NULL OR NOT EXISTS (" +
           "    SELECT b FROM Booking b " +
           "    WHERE b.room = r " +
           "    AND b.status IN :statuses " +
           "    AND :checkIn < b.checkOut " +
           "    AND :checkOut > b.checkIn" +
           ")) " +
           "ORDER BY r.roomNumber")
    List<Room> searchRoomsAdvanced(
            @Param("type") RoomType type,
            @Param("capacity") Integer capacity,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("amenity") String amenity,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("statuses") Collection<com.stayease.model.BookingStatus> statuses
    );

    @Query(value = "SELECT r FROM Room r WHERE r.active = true AND r.underMaintenance = false " +
           "AND (:type IS NULL OR r.type = :type) " +
           "AND (:capacity IS NULL OR r.capacity >= :capacity) " +
           "AND (:minPrice IS NULL OR r.pricePerNight >= :minPrice) " +
           "AND (:maxPrice IS NULL OR r.pricePerNight <= :maxPrice) " +
           "AND (:amenity IS NULL OR LOWER(r.amenities) LIKE LOWER(CONCAT('%', :amenity, '%'))) " +
           "AND (:checkIn IS NULL OR :checkOut IS NULL OR NOT EXISTS (" +
           "    SELECT b FROM Booking b " +
           "    WHERE b.room = r " +
           "    AND b.status IN :statuses " +
           "    AND :checkIn < b.checkOut " +
           "    AND :checkOut > b.checkIn" +
           ")) " +
           "ORDER BY r.roomNumber",
           countQuery = "SELECT count(r) FROM Room r WHERE r.active = true AND r.underMaintenance = false " +
           "AND (:type IS NULL OR r.type = :type) " +
           "AND (:capacity IS NULL OR r.capacity >= :capacity) " +
           "AND (:minPrice IS NULL OR r.pricePerNight >= :minPrice) " +
           "AND (:maxPrice IS NULL OR r.pricePerNight <= :maxPrice) " +
           "AND (:amenity IS NULL OR LOWER(r.amenities) LIKE LOWER(CONCAT('%', :amenity, '%'))) " +
           "AND (:checkIn IS NULL OR :checkOut IS NULL OR NOT EXISTS (" +
           "    SELECT b FROM Booking b " +
           "    WHERE b.room = r " +
           "    AND b.status IN :statuses " +
           "    AND :checkIn < b.checkOut " +
           "    AND :checkOut > b.checkIn" +
           "))")
    Page<Room> searchRoomsAdvanced(
            @Param("type") RoomType type,
            @Param("capacity") Integer capacity,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("amenity") String amenity,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("statuses") Collection<com.stayease.model.BookingStatus> statuses,
            Pageable pageable
    );

    Optional<Room> findByIdAndActiveTrue(Long id);

    List<Room> findAllByOrderByRoomNumberAsc();

    Optional<Room> findByRoomNumberIgnoreCase(String roomNumber);

    boolean existsByRoomNumberIgnoreCase(String roomNumber);

    boolean existsByRoomNumberIgnoreCaseAndIdNot(String roomNumber, Long id);

    long countByActiveTrue();
}

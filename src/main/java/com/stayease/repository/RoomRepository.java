package com.stayease.repository;

import com.stayease.model.Room;
import com.stayease.model.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    Optional<Room> findByIdAndActiveTrue(Long id);

    List<Room> findAllByOrderByRoomNumberAsc();

    Optional<Room> findByRoomNumberIgnoreCase(String roomNumber);

    boolean existsByRoomNumberIgnoreCase(String roomNumber);

    boolean existsByRoomNumberIgnoreCaseAndIdNot(String roomNumber, Long id);
}

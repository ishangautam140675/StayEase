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

    boolean existsByRoomId(Long roomId);
}

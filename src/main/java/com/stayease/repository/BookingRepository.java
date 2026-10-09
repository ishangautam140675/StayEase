package com.stayease.repository;

import com.stayease.model.Booking;
import com.stayease.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Booking} entities.
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserOrderByCreatedAtDesc(User user);

    boolean existsByRoomId(Long roomId);
}

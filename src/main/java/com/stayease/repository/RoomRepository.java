package com.stayease.repository;

import com.stayease.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Room} entities.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
}

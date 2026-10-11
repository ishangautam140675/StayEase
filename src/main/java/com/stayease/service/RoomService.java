package com.stayease.service;

import com.stayease.dto.RoomForm;
import com.stayease.exception.DuplicateRoomNumberException;
import com.stayease.exception.RoomNotFoundException;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for public room catalog browsing, search, and administrative room management.
 */
@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public RoomService(RoomRepository roomRepository, BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    // ── Public Catalog Methods ─────────────────────────────────────────────

    /**
     * Finds active rooms matching optional filtering criteria.
     */
    public List<Room> searchActiveRooms(RoomType type, Integer capacity, BigDecimal minPrice, BigDecimal maxPrice) {
        return roomRepository.searchActiveRooms(type, capacity, minPrice, maxPrice);
    }

    /**
     * Finds active rooms matching optional filtering criteria with pagination.
     */
    public Page<Room> searchActiveRooms(RoomType type, Integer capacity, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        return roomRepository.searchActiveRooms(type, capacity, minPrice, maxPrice, pageable);
    }

    /**
     * Retrieves an active room by ID.
     *
     * @throws RoomNotFoundException if room does not exist or is inactive
     */
    public Room getActiveRoom(Long id) {
        return roomRepository.findById(id)
                .filter(Room::isActive)
                .orElseThrow(() -> new RoomNotFoundException("Room not found or inactive with id: " + id));
    }

    /**
     * Finds active rooms with capacity >= guests that have no blocking bookings overlapping the requested dates.
     */
    public List<Room> findAvailableRooms(java.time.LocalDate checkIn, java.time.LocalDate checkOut, Integer guests) {
        if (checkIn == null || checkOut == null || guests == null) {
            return java.util.Collections.emptyList();
        }
        return roomRepository.findAvailableRooms(checkIn, checkOut, guests, com.stayease.model.BookingStatus.BLOCKING);
    }

    // ── Admin CRUD Methods ─────────────────────────────────────────────────

    /**
     * Retrieves all rooms in the system (active and inactive) ordered by room number.
     */
    public List<Room> findAllForAdmin() {
        return roomRepository.findAllByOrderByRoomNumberAsc();
    }

    /**
     * Retrieves a room by ID for editing (active or inactive).
     *
     * @throws RoomNotFoundException if room does not exist
     */
    public Room getForEdit(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new RoomNotFoundException("Room not found with id: " + id));
    }

    /**
     * Finds active rooms matching advanced search criteria (type, capacity, price range, amenity, and date availability).
     */
    public List<Room> searchRoomsAdvanced(RoomType type, Integer capacity, BigDecimal minPrice, BigDecimal maxPrice,
                                          String amenity, java.time.LocalDate checkIn, java.time.LocalDate checkOut) {
        String cleanAmenity = (amenity != null && !amenity.trim().isEmpty()) ? amenity.trim() : null;
        return roomRepository.searchRoomsAdvanced(type, capacity, minPrice, maxPrice, cleanAmenity,
                checkIn, checkOut, com.stayease.model.BookingStatus.BLOCKING);
    }

    /**
     * Finds active rooms matching advanced search criteria with pagination.
     */
    public Page<Room> searchRoomsAdvanced(RoomType type, Integer capacity, BigDecimal minPrice, BigDecimal maxPrice,
                                          String amenity, java.time.LocalDate checkIn, java.time.LocalDate checkOut,
                                          Pageable pageable) {
        String cleanAmenity = (amenity != null && !amenity.trim().isEmpty()) ? amenity.trim() : null;
        return roomRepository.searchRoomsAdvanced(type, capacity, minPrice, maxPrice, cleanAmenity,
                checkIn, checkOut, com.stayease.model.BookingStatus.BLOCKING, pageable);
    }

    /**
     * Creates a new room. Room numbers are trimmed and checked case-insensitively.
     *
     * @throws DuplicateRoomNumberException if the room number already exists
     */
    @Transactional
    public Room create(RoomForm form) {
        String trimmedNumber = form.getRoomNumber() != null ? form.getRoomNumber().trim() : "";

        if (roomRepository.existsByRoomNumberIgnoreCase(trimmedNumber)) {
            throw new DuplicateRoomNumberException("Room number '" + trimmedNumber + "' already exists.");
        }

        Room room = new Room(
                trimmedNumber,
                form.getType(),
                form.getCapacity(),
                form.getPricePerNight(),
                form.getDescription(),
                form.isActive(),
                form.isUnderMaintenance(),
                form.getAmenities() != null ? form.getAmenities().trim() : null
        );

        return roomRepository.save(room);
    }

    /**
     * Updates an existing room. Room numbers are trimmed and checked case-insensitively
     * ignoring the room currently being updated.
     *
     * @throws RoomNotFoundException if room does not exist
     * @throws DuplicateRoomNumberException if the updated number conflicts with another room
     */
    @Transactional
    public Room update(Long id, RoomForm form) {
        Room room = getForEdit(id);
        String trimmedNumber = form.getRoomNumber() != null ? form.getRoomNumber().trim() : "";

        if (roomRepository.existsByRoomNumberIgnoreCaseAndIdNot(trimmedNumber, id)) {
            throw new DuplicateRoomNumberException("Room number '" + trimmedNumber + "' already exists.");
        }

        room.setRoomNumber(trimmedNumber);
        room.setType(form.getType());
        room.setCapacity(form.getCapacity());
        room.setPricePerNight(form.getPricePerNight());
        room.setDescription(form.getDescription());
        room.setActive(form.isActive());
        room.setUnderMaintenance(form.isUnderMaintenance());
        room.setAmenities(form.getAmenities() != null ? form.getAmenities().trim() : null);

        return roomRepository.save(room);
    }

    /**
     * Deletes a room by ID. Refuses deletion if the room has bookings.
     *
     * @throws RoomNotFoundException if room does not exist
     * @throws IllegalStateException if the room has associated bookings
     */
    @Transactional
    public void delete(Long id) {
        Room room = getForEdit(id);

        if (bookingRepository.existsByRoomId(id)) {
            throw new IllegalStateException("This room has bookings. Deactivate it instead.");
        }

        roomRepository.delete(room);
    }
}

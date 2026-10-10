package com.stayease.service;

import com.stayease.dto.RoomForm;
import com.stayease.exception.DuplicateRoomNumberException;
import com.stayease.exception.RoomNotFoundException;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class RoomServiceTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    private Room singleActive;
    private Room doubleActive;
    private Room tripleActive;
    private Room doubleInactive;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        roomRepository.flush();

        singleActive = roomRepository.save(new Room(
                "S101", RoomType.SINGLE, 1, new BigDecimal("600.00"), "Active single", true
        ));
        doubleActive = roomRepository.save(new Room(
                "S102", RoomType.DOUBLE, 2, new BigDecimal("900.00"), "Active double", true
        ));
        tripleActive = roomRepository.save(new Room(
                "S103", RoomType.TRIPLE, 3, new BigDecimal("1200.00"), "Active triple", true
        ));
        doubleInactive = roomRepository.save(new Room(
                "S104", RoomType.DOUBLE, 2, new BigDecimal("850.00"), "Inactive double", false
        ));
        roomRepository.flush();
    }

    // ── Public Catalog Filtering Tests ─────────────────────────────────────

    @Test
    @DisplayName("Filter by type on its own returns matching active rooms only")
    void searchActiveRooms_filterByType() {
        List<Room> results = roomService.searchActiveRooms(RoomType.DOUBLE, null, null, null);
        assertThat(results)
                .extracting(Room::getRoomNumber)
                .containsExactly("S102"); // doubleInactive (S104) is excluded
    }

    @Test
    @DisplayName("Filter by capacity on its own returns rooms with capacity >= requested")
    void searchActiveRooms_filterByCapacity() {
        List<Room> results = roomService.searchActiveRooms(null, 2, null, null);
        assertThat(results)
                .extracting(Room::getRoomNumber)
                .containsExactly("S102", "S103");
    }

    @Test
    @DisplayName("Filter by minPrice on its own returns rooms with price >= minPrice")
    void searchActiveRooms_filterByMinPrice() {
        List<Room> results = roomService.searchActiveRooms(null, null, new BigDecimal("900.00"), null);
        assertThat(results)
                .extracting(Room::getRoomNumber)
                .containsExactly("S102", "S103");
    }

    @Test
    @DisplayName("Filter by maxPrice on its own returns rooms with price <= maxPrice")
    void searchActiveRooms_filterByMaxPrice() {
        List<Room> results = roomService.searchActiveRooms(null, null, null, new BigDecimal("900.00"));
        assertThat(results)
                .extracting(Room::getRoomNumber)
                .containsExactly("S101", "S102");
    }

    @Test
    @DisplayName("Combined filters return only rooms satisfying all criteria")
    void searchActiveRooms_combinedFilters() {
        List<Room> results = roomService.searchActiveRooms(
                RoomType.DOUBLE, 2, new BigDecimal("800.00"), new BigDecimal("1000.00")
        );
        assertThat(results)
                .extracting(Room::getRoomNumber)
                .containsExactly("S102");
    }

    @Test
    @DisplayName("Inactive rooms are never returned in search results even if matching criteria")
    void searchActiveRooms_inactiveRoomsNeverReturned() {
        List<Room> results = roomService.searchActiveRooms(RoomType.DOUBLE, null, null, null);
        assertThat(results)
                .noneMatch(r -> !r.isActive())
                .extracting(Room::getRoomNumber)
                .doesNotContain("S104");
    }

    @Test
    @DisplayName("getActiveRoom returns active room for valid ID")
    void getActiveRoom_validId_returnsRoom() {
        Room room = roomService.getActiveRoom(singleActive.getId());
        assertThat(room).isNotNull();
        assertThat(room.getRoomNumber()).isEqualTo("S101");
    }

    @Test
    @DisplayName("getActiveRoom throws RoomNotFoundException for unknown ID")
    void getActiveRoom_unknownId_throwsException() {
        assertThatThrownBy(() -> roomService.getActiveRoom(999999L))
                .isInstanceOf(RoomNotFoundException.class)
                .hasMessageContaining("Room not found or inactive");
    }

    @Test
    @DisplayName("getActiveRoom throws RoomNotFoundException for inactive room ID")
    void getActiveRoom_inactiveRoom_throwsException() {
        assertThatThrownBy(() -> roomService.getActiveRoom(doubleInactive.getId()))
                .isInstanceOf(RoomNotFoundException.class)
                .hasMessageContaining("Room not found or inactive");
    }

    // ── Admin Room CRUD Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("findAllForAdmin returns all rooms including inactive ones")
    void findAllForAdmin_returnsAllRooms() {
        List<Room> all = roomService.findAllForAdmin();
        assertThat(all).hasSize(4)
                .extracting(Room::getRoomNumber)
                .contains("S101", "S102", "S103", "S104");
    }

    @Test
    @DisplayName("create room works and persists with trimmed room number")
    void create_validForm_persistsRoom() {
        RoomForm form = new RoomForm(" S105 ", RoomType.DORMITORY, 4, new BigDecimal("1500.00"), "New dorm", true);
        Room created = roomService.create(form);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getRoomNumber()).isEqualTo("S105");
        assertThat(roomRepository.findByRoomNumberIgnoreCase("S105")).isPresent();
    }

    @Test
    @DisplayName("create rejects duplicate room number case-insensitively and with whitespace")
    void create_duplicateRoomNumber_throwsException() {
        RoomForm form = new RoomForm(" s101 ", RoomType.SINGLE, 1, new BigDecimal("700.00"), "Duplicate", true);

        assertThatThrownBy(() -> roomService.create(form))
                .isInstanceOf(DuplicateRoomNumberException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("update room works and persists modifications")
    void update_validForm_updatesRoom() {
        RoomForm form = new RoomForm("S101", RoomType.SINGLE, 1, new BigDecimal("750.00"), "Updated description", true);
        Room updated = roomService.update(singleActive.getId(), form);

        assertThat(updated.getPricePerNight()).isEqualByComparingTo("750.00");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
    }

    @Test
    @DisplayName("update deactivating through update works")
    void update_deactivatingRoom_works() {
        RoomForm form = new RoomForm("S101", RoomType.SINGLE, 1, new BigDecimal("600.00"), "Now inactive", false);
        Room updated = roomService.update(singleActive.getId(), form);

        assertThat(updated.isActive()).isFalse();
        assertThat(roomRepository.findById(singleActive.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    @DisplayName("update rejects duplicate room number conflicting with another room")
    void update_conflictingRoomNumber_throwsException() {
        RoomForm form = new RoomForm(" s102 ", RoomType.SINGLE, 1, new BigDecimal("600.00"), "Conflict with S102", true);

        assertThatThrownBy(() -> roomService.update(singleActive.getId(), form))
                .isInstanceOf(DuplicateRoomNumberException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("delete room works when there are no bookings")
    void delete_withoutBookings_succeeds() {
        Long id = singleActive.getId();
        roomService.delete(id);

        assertThat(roomRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("delete room with existing bookings is refused with expected message")
    void delete_withBookings_throwsIllegalStateException() {
        User user = userRepository.save(new User(
                "Booker", "booker@test.com", "hash", "1234567890", Role.CUSTOMER
        ));

        bookingRepository.save(new Booking(
                user,
                singleActive,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3),
                1,
                BookingStatus.APPROVED,
                null
        ));

        assertThatThrownBy(() -> roomService.delete(singleActive.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This room has bookings. Deactivate it instead.");
    }
}

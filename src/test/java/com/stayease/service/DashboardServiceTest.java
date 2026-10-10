package com.stayease.service;

import com.stayease.dto.AdminDashboardStats;
import com.stayease.dto.CustomerDashboardView;
import com.stayease.dto.MonthBookingCount;
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

@SpringBootTest
@Transactional
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    private User customer1;
    private User customer2;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.findByEmail("dash.cust1@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("dash.cust2@stayease.com").ifPresent(userRepository::delete);

        customer1 = userRepository.save(new User(
                "Dash Customer 1", "dash.cust1@stayease.com", "Pass123", "1111111111", Role.CUSTOMER
        ));
        customer2 = userRepository.save(new User(
                "Dash Customer 2", "dash.cust2@stayease.com", "Pass123", "2222222222", Role.CUSTOMER
        ));
    }

    @Test
    @DisplayName("With 4 active rooms and 2 occupied today the occupancy rate is 50.0")
    void occupancyRate_withFourActiveRoomsAndTwoOccupied_isFiftyPercent() {
        Room r1 = roomRepository.save(new Room("101", RoomType.SINGLE, 1, BigDecimal.valueOf(100), "Room 1", true));
        Room r2 = roomRepository.save(new Room("102", RoomType.SINGLE, 1, BigDecimal.valueOf(100), "Room 2", true));
        Room r3 = roomRepository.save(new Room("103", RoomType.SINGLE, 1, BigDecimal.valueOf(100), "Room 3", true));
        Room r4 = roomRepository.save(new Room("104", RoomType.SINGLE, 1, BigDecimal.valueOf(100), "Room 4", true));

        LocalDate today = LocalDate.now();
        // r1 occupied today: checkIn <= today < checkOut, APPROVED
        bookingRepository.save(new Booking(customer1, r1, today.minusDays(1), today.plusDays(2), 1, BookingStatus.APPROVED, null));
        // r2 occupied today: checkIn <= today < checkOut, APPROVED
        bookingRepository.save(new Booking(customer2, r2, today, today.plusDays(1), 1, BookingStatus.APPROVED, null));

        AdminDashboardStats stats = dashboardService.adminStats();
        assertThat(stats.totalRooms()).isEqualTo(4);
        assertThat(stats.activeRooms()).isEqualTo(4);
        assertThat(stats.occupiedToday()).isEqualTo(2);
        assertThat(stats.availableToday()).isEqualTo(2);
        assertThat(stats.occupancyRate()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("No active rooms gives occupancy rate of 0")
    void occupancyRate_noActiveRooms_givesZero() {
        // Inactive room
        roomRepository.save(new Room("101", RoomType.SINGLE, 1, BigDecimal.valueOf(100), "Room 1", false));

        AdminDashboardStats stats = dashboardService.adminStats();
        assertThat(stats.totalRooms()).isEqualTo(1);
        assertThat(stats.activeRooms()).isEqualTo(0);
        assertThat(stats.occupiedToday()).isEqualTo(0);
        assertThat(stats.availableToday()).isEqualTo(0);
        assertThat(stats.occupancyRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("CANCELLED, REJECTED and PENDING bookings are not counted as occupied")
    void cancelledRejectedAndPending_areNotCountedAsOccupied() {
        Room r1 = roomRepository.save(new Room("101", RoomType.SINGLE, 1, BigDecimal.valueOf(100), "Room 1", true));
        Room r2 = roomRepository.save(new Room("102", RoomType.SINGLE, 1, BigDecimal.valueOf(100), "Room 2", true));
        Room r3 = roomRepository.save(new Room("103", RoomType.SINGLE, 1, BigDecimal.valueOf(100), "Room 3", true));

        LocalDate today = LocalDate.now();
        // PENDING today on r1
        bookingRepository.save(new Booking(customer1, r1, today.minusDays(1), today.plusDays(2), 1, BookingStatus.PENDING, null));
        // REJECTED today on r2
        bookingRepository.save(new Booking(customer1, r2, today.minusDays(1), today.plusDays(2), 1, BookingStatus.REJECTED, "Denied"));
        // CANCELLED today on r3
        bookingRepository.save(new Booking(customer1, r3, today.minusDays(1), today.plusDays(2), 1, BookingStatus.CANCELLED, null));

        AdminDashboardStats stats = dashboardService.adminStats();
        assertThat(stats.activeRooms()).isEqualTo(3);
        assertThat(stats.occupiedToday()).isEqualTo(0);
        assertThat(stats.availableToday()).isEqualTo(3);
        assertThat(stats.occupancyRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("The pending count and the monthly counts are correct")
    void pendingCountAndMonthlyCounts_areCorrect() {
        Room r1 = roomRepository.save(new Room("101", RoomType.SINGLE, 1, BigDecimal.valueOf(100), "Room 1", true));

        LocalDate today = LocalDate.now();
        // 2 pending bookings
        bookingRepository.save(new Booking(customer1, r1, today.plusDays(5), today.plusDays(8), 1, BookingStatus.PENDING, null));
        bookingRepository.save(new Booking(customer2, r1, today.plusDays(10), today.plusDays(12), 1, BookingStatus.PENDING, null));
        // 1 approved booking
        bookingRepository.save(new Booking(customer1, r1, today.plusDays(15), today.plusDays(18), 1, BookingStatus.APPROVED, null));

        AdminDashboardStats stats = dashboardService.adminStats();
        assertThat(stats.pendingRequests()).isEqualTo(2);

        // Monthly counts has 6 items
        List<MonthBookingCount> monthly = stats.bookingsPerMonth();
        assertThat(monthly).hasSize(6);
        // The last month (current month) should have 3 bookings created
        MonthBookingCount currentMonthCount = monthly.get(monthly.size() - 1);
        assertThat(currentMonthCount.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("The customer view only contains that customer's bookings")
    void customerView_onlyContainsThatCustomersBookings() {
        Room r1 = roomRepository.save(new Room("101", RoomType.SINGLE, 1, BigDecimal.valueOf(100), "Room 1", true));
        Room r2 = roomRepository.save(new Room("102", RoomType.SINGLE, 1, BigDecimal.valueOf(100), "Room 2", true));

        LocalDate today = LocalDate.now();

        // Customer 1 bookings:
        // 1 upcoming APPROVED (starts soonest)
        Booking c1Next = bookingRepository.save(new Booking(
                customer1, r1, today.plusDays(2), today.plusDays(5), 1, BookingStatus.APPROVED, null
        ));
        // 1 upcoming PENDING
        Booking c1Pending = bookingRepository.save(new Booking(
                customer1, r1, today.plusDays(10), today.plusDays(12), 1, BookingStatus.PENDING, null
        ));
        // 1 past APPROVED
        Booking c1Past = bookingRepository.save(new Booking(
                customer1, r1, today.minusDays(6), today.minusDays(2), 1, BookingStatus.APPROVED, null
        ));

        // Customer 2 bookings (must NOT appear in Customer 1's view):
        bookingRepository.save(new Booking(
                customer2, r2, today.plusDays(1), today.plusDays(4), 1, BookingStatus.APPROVED, null
        ));
        bookingRepository.save(new Booking(
                customer2, r2, today.plusDays(6), today.plusDays(8), 1, BookingStatus.PENDING, null
        ));

        CustomerDashboardView view1 = dashboardService.customerView(customer1.getEmail());

        assertThat(view1.upcomingCount()).isEqualTo(2);
        assertThat(view1.pendingCount()).isEqualTo(1);
        assertThat(view1.pastCount()).isEqualTo(1);
        assertThat(view1.nextStay()).isNotNull();
        assertThat(view1.nextStay().getId()).isEqualTo(c1Next.getId());
        assertThat(view1.upcomingBookings()).hasSize(2);
        assertThat(view1.upcomingBookings())
                .extracting(Booking::getId)
                .containsExactly(c1Next.getId(), c1Pending.getId());

        // Check customer 2 view as well
        CustomerDashboardView view2 = dashboardService.customerView(customer2.getEmail());
        assertThat(view2.upcomingCount()).isEqualTo(2);
        assertThat(view2.pendingCount()).isEqualTo(1);
        assertThat(view2.pastCount()).isEqualTo(0);
        assertThat(view2.upcomingBookings())
                .allMatch(b -> b.getUser().getId().equals(customer2.getId()));
    }
}

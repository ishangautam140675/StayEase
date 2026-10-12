package com.stayease.config;

import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.PaymentStatus;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Boots the application with an initial administrator account and sample rooms if configured.
 *
 * <p>1. If configured with {@code ADMIN_EMAIL} and {@code ADMIN_PASSWORD}, creates a bootstrap
 * {@link Role#ADMIN} user if none exists with that email.</p>
 * <p>2. If the {@code rooms} table is empty, seeds 8 diverse sample rooms (including one inactive room)
 * for testing and browsing.</p>
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.stayease.repository.BookingRepository bookingRepository;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.demo-users.enabled:true}")
    private boolean demoUsersEnabled = true;

    public DataSeeder(UserRepository userRepository,
                      RoomRepository roomRepository,
                      PasswordEncoder passwordEncoder,
                      com.stayease.repository.BookingRepository bookingRepository) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.passwordEncoder = passwordEncoder;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public void run(String... args) {
        seedAdminUser();
        seedSampleRooms();
        if (demoUsersEnabled) {
            seedDemoCustomerUsers();
            seedSampleBookings();
        }
    }

    private void seedAdminUser() {
        String effectiveEmail = (adminEmail != null && !adminEmail.isBlank())
                ? adminEmail.trim().toLowerCase()
                : (demoUsersEnabled ? "admin@stayease.com" : null);

        String effectivePassword = (adminPassword != null && !adminPassword.isBlank())
                ? adminPassword
                : (demoUsersEnabled ? "Admin@1234" : null);

        if (effectiveEmail == null || effectivePassword == null) {
            logger.warn("Admin seed credentials (ADMIN_EMAIL / ADMIN_PASSWORD) are not set. Skipping bootstrap admin creation.");
            return;
        }

        if (userRepository.existsByEmail(effectiveEmail)) {
            logger.info("Admin account already exists for email: {}. Skipping seed.", effectiveEmail);
            return;
        }

        User adminUser = new User(
                "Administrator",
                effectiveEmail,
                passwordEncoder.encode(effectivePassword),
                "9876543211",
                Role.ADMIN
        );
        adminUser.setEmailVerified(true);

        userRepository.save(adminUser);
        logger.info("Bootstrap ADMIN account successfully created for email: {}", effectiveEmail);
    }

    private void seedDemoCustomerUsers() {
        seedSingleCustomer("Demo Customer", "customer@stayease.com", "Customer@1234", "9876543210");
        seedSingleCustomer("John Traveler", "john.traveler@example.com", "Password123", "9876543212");
    }

    private void seedSingleCustomer(String name, String email, String password, String phone) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            return;
        }

        User customer = new User(
                name,
                normalizedEmail,
                passwordEncoder.encode(password),
                phone,
                Role.CUSTOMER
        );
        customer.setEmailVerified(true);
        userRepository.save(customer);
        logger.info("Bootstrap CUSTOMER account successfully created for email: {}", normalizedEmail);
    }

    private void seedSampleRooms() {
        if (roomRepository.count() == 0) {
            List<Room> sampleRooms = List.of(
                    new Room("101", RoomType.SINGLE, 1, new BigDecimal("550.00"), "Cozy single room with a dedicated study desk and quiet garden view.", true, false, "High-Speed Wi-Fi, Study Desk, Garden View"),
                    new Room("102", RoomType.DOUBLE, 2, new BigDecimal("850.00"), "Spacious double bedroom with twin beds and high-speed Wi-Fi.", true, false, "High-Speed Wi-Fi, Air Conditioning, Twin Beds"),
                    new Room("103", RoomType.TRIPLE, 3, new BigDecimal("1150.00"), "Well-ventilated triple room perfect for small student groups.", true, false, "Wi-Fi, Attached Bath, Study Desks"),
                    new Room("104", RoomType.DORMITORY, 4, new BigDecimal("1450.00"), "Modern dormitory accommodation with private lockers and ensuite bath.", true, false, "Wi-Fi, Lockers, Ensuite Bath"),
                    new Room("201", RoomType.SINGLE, 1, new BigDecimal("650.00"), "Quiet executive single room with natural sunlight and ergonomic chair.", true, false, "High-Speed Wi-Fi, Ergonomic Chair, Sunlit Balcony"),
                    new Room("202", RoomType.DOUBLE, 2, new BigDecimal("950.00"), "Premium double room featuring air conditioning and attached balcony.", true, false, "Air Conditioning, Balcony, Wi-Fi"),
                    new Room("203", RoomType.TRIPLE, 3, new BigDecimal("1250.00"), "Deluxe triple sharing accommodation with individual wardrobes.", true, false, "Wardrobes, Wi-Fi, Study Lamp"),
                    new Room("204", RoomType.DOUBLE, 2, new BigDecimal("800.00"), "Under maintenance room for testing maintenance filtering.", true, true, "Wi-Fi, AC")
            );

            roomRepository.saveAll(sampleRooms);
            logger.info("Successfully seeded {} sample rooms into the database.", sampleRooms.size());
        }
    }

    private void seedSampleBookings() {
        if (bookingRepository.count() >= 5) {
            logger.info("Database already contains {} bookings. Skipping sample booking seeding.", bookingRepository.count());
            return;
        }

        Room r101 = roomRepository.findByRoomNumberIgnoreCase("101").orElse(null);
        Room r102 = roomRepository.findByRoomNumberIgnoreCase("102").orElse(null);
        Room r103 = roomRepository.findByRoomNumberIgnoreCase("103").orElse(null);
        Room r104 = roomRepository.findByRoomNumberIgnoreCase("104").orElse(null);
        Room r201 = roomRepository.findByRoomNumberIgnoreCase("201").orElse(null);
        Room r202 = roomRepository.findByRoomNumberIgnoreCase("202").orElse(null);
        Room r203 = roomRepository.findByRoomNumberIgnoreCase("203").orElse(null);

        if (r101 == null || r102 == null) {
            logger.warn("Rooms not found. Skipping sample booking seeding.");
            return;
        }

        User uP11 = getOrCreateGuest("Phase 11 Customer", "cust_p11_9094@stayease.com", "9876543201");
        User uCustB = getOrCreateGuest("Customer B", "customer_b_1595050510@stayease.com", "9876543202");
        User uCustA = getOrCreateGuest("Customer A", "customer_a_760700363@stayease.com", "9876543203");
        User uStayCust = getOrCreateGuest("StayEase Customer", "customer_1988396510@stayease.com", "9876543204");

        User uElena = getOrCreateGuest("Elena Rostova", "elena.rostova@example.com", "9876543205");
        User uLiam = getOrCreateGuest("Liam O'Connor", "liam.oconnor@example.com", "9876543206");
        User uAmina = getOrCreateGuest("Amina Yusuf", "amina.yusuf@example.com", "9876543207");
        User uMarcus = getOrCreateGuest("Marcus Vance", "marcus.vance@example.com", "9876543208");

        User uPriya = getOrCreateGuest("Priya Sharma", "priya.sharma@example.com", "9876543209");
        User uCarlos = getOrCreateGuest("Carlos Mendez", "carlos.mendez@example.com", "9876543210");

        User uSophie = getOrCreateGuest("Sophie Martin", "sophie.martin@example.com", "9876543213");
        User uRajesh = getOrCreateGuest("Rajesh Patel", "rajesh.patel@example.com", "9876543214");

        User uDavid = getOrCreateGuest("David Kim", "david.kim@example.com", "9876543215");
        User uOliver = getOrCreateGuest("Oliver Schmidt", "oliver.schmidt@example.com", "9876543216");
        User uChloe = getOrCreateGuest("Chloe Dupont", "chloe.dupont@example.com", "9876543217");
        User uEthan = getOrCreateGuest("Ethan Wright", "ethan.wright@example.com", "9876543218");

        LocalDate today = LocalDate.now();
        List<Booking> list = new ArrayList<>();

        // 1-4. Pending bookings matching user's reference
        list.add(buildBooking(uP11, r101, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), 1,
                BookingStatus.PENDING, PaymentStatus.FAILED, "Late check-in requested", null, null, null));
        list.add(buildBooking(uStayCust, r101, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 14), 1,
                BookingStatus.PENDING, PaymentStatus.FAILED, "Requested quiet room near garden view", null, null, null));
        list.add(buildBooking(uCustA, r101, LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 15), 1,
                BookingStatus.PENDING, PaymentStatus.FAILED, null, null, null, null));
        list.add(buildBooking(uCustB, r101, LocalDate.of(2026, 12, 15), LocalDate.of(2026, 12, 18), 1,
                BookingStatus.PENDING, PaymentStatus.FAILED, null, null, null, null));

        // 5-8. Approved bookings
        list.add(buildBooking(uElena, r102, LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 22), 2,
                BookingStatus.APPROVED, PaymentStatus.SUCCESSFUL, "Corporate guest; requires VAT invoice", null, null, null));
        list.add(buildBooking(uLiam, r103, LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 28), 3,
                BookingStatus.APPROVED, PaymentStatus.SUCCESSFUL, "Family vacation; extra bed sheets confirmed", null, null, null));
        list.add(buildBooking(uAmina, r104, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 6), 4,
                BookingStatus.APPROVED, PaymentStatus.SUCCESSFUL, "Student study group semester preparation", null, null, null));
        list.add(buildBooking(uMarcus, r201, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), 1,
                BookingStatus.APPROVED, PaymentStatus.SUCCESSFUL, "Executive guest; early arrival requested", null, null, null));

        // 9-10. Currently in-house (CHECKED_IN)
        list.add(buildBooking(uPriya, r202, today.minusDays(1), today.plusDays(3), 2,
                BookingStatus.CHECKED_IN, PaymentStatus.SUCCESSFUL, "Keycard #202 issued at front desk", null,
                LocalDateTime.now().minusDays(1).withHour(14).withMinute(30), null));
        list.add(buildBooking(uCarlos, r203, today, today.plusDays(4), 3,
                BookingStatus.CHECKED_IN, PaymentStatus.SUCCESSFUL, "Web check-in arrival verified", null,
                LocalDateTime.now().withHour(11).withMinute(15), null));

        // 11-12. Departed stays (CHECKED_OUT)
        list.add(buildBooking(uSophie, r102, today.minusDays(10), today.minusDays(6), 2,
                BookingStatus.CHECKED_OUT, PaymentStatus.SUCCESSFUL, "Exemplary stay; room inspection completed", null,
                LocalDateTime.now().minusDays(10).withHour(15).withMinute(0),
                LocalDateTime.now().minusDays(6).withHour(10).withMinute(45)));
        list.add(buildBooking(uRajesh, r103, today.minusDays(8), today.minusDays(3), 3,
                BookingStatus.CHECKED_OUT, PaymentStatus.SUCCESSFUL, "Checked out smoothly; luggage storage requested", null,
                LocalDateTime.now().minusDays(8).withHour(13).withMinute(0),
                LocalDateTime.now().minusDays(3).withHour(11).withMinute(0)));

        // 13. No-Show
        list.add(buildBooking(uDavid, r104, today.minusDays(3), today.minusDays(1), 2,
                BookingStatus.NO_SHOW, PaymentStatus.FAILED, "Phone uncontactable",
                "Guest did not arrive after 24h grace period", null, null));

        // 14. Rejected
        list.add(buildBooking(uOliver, r201, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 18), 1,
                BookingStatus.REJECTED, PaymentStatus.FAILED, null,
                "Room undergoing scheduled maintenance inspection", null, null));

        // 15-16. Cancelled
        list.add(buildBooking(uChloe, r202, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 23), 2,
                BookingStatus.CANCELLED, PaymentStatus.REFUNDED, "Full refund processed via payment portal",
                "Guest cancelled due to flight schedule changes", null, null));
        list.add(buildBooking(uEthan, r203, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 29), 2,
                BookingStatus.CANCELLED, PaymentStatus.UNPAID, null,
                "Cancelled by guest within grace window", null, null));

        bookingRepository.saveAll(list);
        logger.info("Successfully seeded {} sample bookings across all operational statuses into the database.", list.size());
    }

    private User getOrCreateGuest(String fullName, String email, String phone) {
        String normalizedEmail = email.trim().toLowerCase();
        return userRepository.findByEmail(normalizedEmail).orElseGet(() -> {
            User guest = new User(
                    fullName,
                    normalizedEmail,
                    passwordEncoder.encode("Guest@1234"),
                    phone,
                    Role.CUSTOMER
            );
            guest.setEmailVerified(true);
            return userRepository.save(guest);
        });
    }

    private Booking buildBooking(User guest, Room room, LocalDate checkIn, LocalDate checkOut,
                                 int guests, BookingStatus status, PaymentStatus paymentStatus,
                                 String operationalNotes, String adminRemark,
                                 LocalDateTime checkedInAt, LocalDateTime checkedOutAt) {
        Booking b = new Booking(guest, room, checkIn, checkOut, guests, status, adminRemark);
        b.setPaymentStatus(paymentStatus);
        b.setOperationalNotes(operationalNotes);
        b.setCheckedInAt(checkedInAt);
        b.setCheckedOutAt(checkedOutAt);
        return b;
    }
}

package com.stayease.config;

import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    public DataSeeder(UserRepository userRepository,
                      RoomRepository roomRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedAdminUser();
        seedSampleRooms();
    }

    private void seedAdminUser() {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            logger.warn("Admin seed credentials (ADMIN_EMAIL / ADMIN_PASSWORD) are not set. Skipping bootstrap admin creation.");
            return;
        }

        String normalizedEmail = adminEmail.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            logger.info("Admin account already exists for email: {}. Skipping seed.", normalizedEmail);
            return;
        }

        User adminUser = new User(
                "Administrator",
                normalizedEmail,
                passwordEncoder.encode(adminPassword),
                null,
                Role.ADMIN
        );

        userRepository.save(adminUser);
        logger.info("Bootstrap ADMIN account successfully created for email: {}", normalizedEmail);
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
}

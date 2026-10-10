package com.stayease.config;

import com.stayease.model.Role;
import com.stayease.model.User;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DataSeederTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("DataSeeder creates the admin once and does not create a duplicate on a second run")
    void dataSeeder_createsAdminOnce_noDuplicateOnSecondRun() {
        DataSeeder seeder = new DataSeeder(userRepository, roomRepository, passwordEncoder);
        String adminEmail = "seedadmin@stayease.com";
        ReflectionTestUtils.setField(seeder, "adminEmail", adminEmail);
        ReflectionTestUtils.setField(seeder, "adminPassword", "Admin1234");

        // Ensure clean state for this email
        userRepository.findByEmail(adminEmail).ifPresent(userRepository::delete);
        long initialCount = userRepository.count();

        // 1st run
        seeder.run();
        assertThat(userRepository.findByEmail(adminEmail)).isPresent();
        User created = userRepository.findByEmail(adminEmail).orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches("Admin1234", created.getPasswordHash())).isTrue();
        assertThat(userRepository.count()).isEqualTo(initialCount + 1);

        // 2nd run
        seeder.run();
        assertThat(userRepository.count()).isEqualTo(initialCount + 1);
    }

    @Test
    @DisplayName("DataSeeder seeds 8 sample rooms when empty and does not duplicate on second run")
    void dataSeeder_seedsRoomsOnce() {
        DataSeeder seeder = new DataSeeder(userRepository, roomRepository, passwordEncoder);
        ReflectionTestUtils.setField(seeder, "adminEmail", "");
        ReflectionTestUtils.setField(seeder, "adminPassword", "");

        long roomCount = roomRepository.count();
        assertThat(roomCount).isGreaterThanOrEqualTo(8);

        // Run again — must remain identical
        seeder.run();
        assertThat(roomRepository.count()).isEqualTo(roomCount);
    }

    @Test
    @DisplayName("DataSeeder does not create user when credentials are blank")
    void dataSeeder_skipsWhenBlank() {
        DataSeeder seeder = new DataSeeder(userRepository, roomRepository, passwordEncoder);
        ReflectionTestUtils.setField(seeder, "adminEmail", "");
        ReflectionTestUtils.setField(seeder, "adminPassword", "");

        long initialCount = userRepository.count();
        seeder.run();
        assertThat(userRepository.count()).isEqualTo(initialCount);
    }
}

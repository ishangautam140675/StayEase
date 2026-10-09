package com.stayease;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: verifies that the Spring application context loads without errors.
 * If any bean configuration is broken, this test fails immediately.
 */
@SpringBootTest
class StayEaseApplicationTests {

    @Test
    void contextLoads() {
        // If this method runs without throwing an exception,
        // the full application context started successfully.
    }
}

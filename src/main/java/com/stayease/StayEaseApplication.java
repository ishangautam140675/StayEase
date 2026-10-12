package com.stayease;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * Entry point for the StayEase application.
 * @SpringBootApplication enables component scan, auto-configuration, and
 * configuration properties scanning for the com.stayease package.
 */
@SpringBootApplication
public class StayEaseApplication {

    @Value("${app.timezone:Asia/Kolkata}")
    private String appTimezone = "Asia/Kolkata";

    @PostConstruct
    public void init() {
        // Enforce application timezone so all server-side operations
        // (receipts, booking requests, approvals, notifications, audit logs, popups)
        // perfectly synchronize with the real time and date of the device.
        TimeZone.setDefault(TimeZone.getTimeZone(appTimezone != null && !appTimezone.isBlank() ? appTimezone : "Asia/Kolkata"));
    }

    public static void main(String[] args) {
        String tz = System.getenv("TZ");
        if (tz == null || tz.isBlank()) {
            tz = System.getProperty("user.timezone", "Asia/Kolkata");
        }
        TimeZone.setDefault(TimeZone.getTimeZone(tz));
        SpringApplication.run(StayEaseApplication.class, args);
    }
}

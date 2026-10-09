package com.stayease;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the StayEase application.
 * @SpringBootApplication enables component scan, auto-configuration, and
 * configuration properties scanning for the com.stayease package.
 */
@SpringBootApplication
public class StayEaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(StayEaseApplication.class, args);
    }
}

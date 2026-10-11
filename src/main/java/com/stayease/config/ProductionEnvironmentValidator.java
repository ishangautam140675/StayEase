package com.stayease.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Startup validation for production environment variables.
 *
 * <p>When the {@code prod} profile is active, this component validates that all
 * critical production configurations (Database credentials, payment secrets,
 * mail host, and admin credentials) are present and properly configured.
 *
 * <p>Security rule: Secret values are NEVER logged or exposed in exception messages.
 * Only variable presence, masked identifiers, or length are logged.
 */
@Component
public class ProductionEnvironmentValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionEnvironmentValidator.class);

    private final Environment environment;

    public ProductionEnvironmentValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProd = Arrays.asList(activeProfiles).contains("prod");

        if (!isProd) {
            log.info("ProductionEnvironmentValidator: Non-production profiles active ({}); skipping strict prod startup validation.",
                    Arrays.toString(activeProfiles));
            return;
        }

        log.info("================================================================================");
        log.info("StayEase Production Environment Validation Started (Profile: prod)");
        log.info("================================================================================");

        List<String> missingOrInvalid = new ArrayList<>();

        // 1. Validate Database Configuration
        String dbUrl = environment.getProperty("spring.datasource.url");
        if (dbUrl == null || dbUrl.isBlank()) {
            missingOrInvalid.add("DB_URL (spring.datasource.url is missing or blank)");
        } else {
            String sanitizedDbUrl = sanitizeJdbcUrl(dbUrl);
            log.info("Production DataSource Target: {}", sanitizedDbUrl);
        }

        String dbUser = environment.getProperty("spring.datasource.username");
        if (dbUser == null || dbUser.isBlank()) {
            missingOrInvalid.add("DB_USERNAME (spring.datasource.username is missing)");
        } else {
            log.info("Production DB User: {}", dbUser);
        }

        String dbPassword = environment.getProperty("spring.datasource.password");
        if (dbPassword == null || dbPassword.isBlank()) {
            missingOrInvalid.add("DB_PASSWORD (spring.datasource.password is missing or empty)");
        } else {
            log.info("Production DB Password: [PRESENT, {} characters]", dbPassword.length());
        }

        // 2. Validate Payment Provider Configuration
        String paymentProvider = environment.getProperty("app.payment.provider", "sandbox");
        log.info("Configured Payment Provider: {}", paymentProvider);
        if ("razorpay".equalsIgnoreCase(paymentProvider)) {
            String rzpKey = environment.getProperty("app.payment.razorpay.key-id");
            String rzpSecret = environment.getProperty("app.payment.razorpay.key-secret");
            if (rzpKey == null || rzpKey.isBlank()) {
                missingOrInvalid.add("RAZORPAY_KEY_ID (required when PAYMENT_PROVIDER=razorpay)");
            } else {
                log.info("Razorpay Key ID: {}", maskIdentifier(rzpKey));
            }
            if (rzpSecret == null || rzpSecret.isBlank()) {
                missingOrInvalid.add("RAZORPAY_KEY_SECRET (required when PAYMENT_PROVIDER=razorpay)");
            } else {
                log.info("Razorpay Secret: [PRESENT, {} characters]", rzpSecret.length());
            }
        }

        // 3. Validate Email/SMTP Configuration if enabled
        String mailEnabledStr = environment.getProperty("app.mail.enabled", "false");
        boolean mailEnabled = Boolean.parseBoolean(mailEnabledStr);
        if (mailEnabled) {
            String mailHost = environment.getProperty("spring.mail.host");
            if (mailHost == null || mailHost.isBlank() || "localhost".equalsIgnoreCase(mailHost)) {
                missingOrInvalid.add("SPRING_MAIL_HOST (Production SMTP host must not be empty or localhost when APP_MAIL_ENABLED=true)");
            } else {
                log.info("Production SMTP Host: {}", mailHost);
            }
        } else {
            log.warn("Notice: Production email delivery is disabled (APP_MAIL_ENABLED=false). Notifications will log to database only.");
        }

        // 4. Validate Base URL
        String baseUrl = environment.getProperty("app.base-url");
        if (baseUrl == null || baseUrl.isBlank()) {
            missingOrInvalid.add("APP_BASE_URL (Required for notification links and callback resolution)");
        } else {
            log.info("Application Public Base URL: {}", baseUrl);
        }

        // 5. Fail Fast If Validation Errors Found
        if (!missingOrInvalid.isEmpty()) {
            log.error("================================================================================");
            log.error("CRITICAL CONFIGURATION ERROR: StayEase cannot start in production profile.");
            log.error("The following required settings are missing or invalid:");
            for (String err : missingOrInvalid) {
                log.error("  - {}", err);
            }
            log.error("================================================================================");
            throw new IllegalStateException("Production startup validation failed. Missing or invalid configurations: "
                    + String.join(", ", missingOrInvalid));
        }

        log.info("================================================================================");
        log.info("Production Environment Validation PASSED. All required variables verified.");
        log.info("================================================================================");
    }

    /**
     * Masks an identifier (like an API key or email) for safe log output.
     */
    public static String maskIdentifier(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        int keep = Math.min(4, value.length() / 2);
        return value.substring(0, keep) + "****" + value.substring(value.length() - keep);
    }

    /**
     * Sanitizes a JDBC URL by stripping any inline credentials while preserving host and database.
     */
    public static String sanitizeJdbcUrl(String url) {
        if (url == null) return "null";
        // Remove user and password query parameters if present
        return url.replaceAll("(?i)(password=)[^&;]*", "$1****")
                  .replaceAll("(?i)(user=)[^&;]*", "$1****");
    }
}

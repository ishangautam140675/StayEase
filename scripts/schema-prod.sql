-- =============================================================================
-- StayEase Production Database Schema (MySQL 8.0+)
-- Character Set: utf8mb4, Collation: utf8mb4_unicode_ci
-- Safe, non-destructive, idempotent creation script with production indexes.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS stayease
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE stayease;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20) DEFAULT NULL,
    role VARCHAR(30) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_users_email UNIQUE (email),
    INDEX idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Rooms Table
CREATE TABLE IF NOT EXISTS rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_number VARCHAR(10) NOT NULL,
    type VARCHAR(30) NOT NULL,
    capacity INT NOT NULL,
    price_per_night DECIMAL(10, 2) NOT NULL,
    description VARCHAR(1000) DEFAULT NULL,
    amenities VARCHAR(500) DEFAULT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    under_maintenance BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_rooms_number UNIQUE (room_number),
    INDEX idx_rooms_catalog (active, under_maintenance, type, capacity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Bookings Table
CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_reference VARCHAR(32) DEFAULT NULL,
    user_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    guests INT NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    price_per_night_at_booking DECIMAL(10, 2) DEFAULT NULL,
    status VARCHAR(30) NOT NULL,
    payment_status VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    idempotency_key VARCHAR(64) DEFAULT NULL,
    admin_remark VARCHAR(500) DEFAULT NULL,
    operational_notes VARCHAR(1000) DEFAULT NULL,
    checked_in_at DATETIME(6) DEFAULT NULL,
    checked_out_at DATETIME(6) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT uq_bookings_reference UNIQUE (booking_reference),
    INDEX idx_bookings_room_dates (room_id, check_in, check_out),
    INDEX idx_bookings_idempotency (idempotency_key),
    INDEX idx_bookings_user_status (user_id, status),
    INDEX idx_bookings_created (created_at),
    INDEX idx_bookings_payment_status (payment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Booking Status History Table
CREATE TABLE IF NOT EXISTS booking_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    from_status VARCHAR(30) DEFAULT NULL,
    to_status VARCHAR(30) NOT NULL,
    changed_by VARCHAR(120) NOT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    timestamp DATETIME(6) NOT NULL,
    CONSTRAINT fk_bsh_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    INDEX idx_bsh_booking_time (booking_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Room Maintenance Schedules Table
CREATE TABLE IF NOT EXISTS room_maintenance_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    scheduled_by VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_rms_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    INDEX idx_rms_room_dates (room_id, start_date, end_date),
    INDEX idx_rms_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Seasonal Price Rules Table
CREATE TABLE IF NOT EXISTS seasonal_price_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_type VARCHAR(30) DEFAULT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    multiplier DECIMAL(4, 2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_seasonal_dates_type (start_date, end_date, room_type),
    INDEX idx_seasonal_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Admin Audit Logs Table
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_email VARCHAR(120) NOT NULL,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id VARCHAR(60) DEFAULT NULL,
    details VARCHAR(1500) DEFAULT NULL,
    ip_address VARCHAR(45) DEFAULT NULL,
    timestamp DATETIME(6) NOT NULL,
    INDEX idx_audit_timestamp (timestamp),
    INDEX idx_audit_admin (admin_email),
    INDEX idx_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Notification Logs Table
CREATE TABLE IF NOT EXISTS notification_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient VARCHAR(150) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(100) DEFAULT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) DEFAULT NULL,
    sent_at DATETIME(6) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_notif_ref (notification_type, reference_id),
    INDEX idx_notif_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Password Reset Tokens Table
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    expiry_date DATETIME(6) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_prt_token UNIQUE (token),
    INDEX idx_prt_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Email Verification Tokens Table
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    expiry_date DATETIME(6) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_evt_token UNIQUE (token),
    INDEX idx_evt_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. Payment Transactions Table
CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    transaction_reference VARCHAR(64) NOT NULL,
    gateway_reference VARCHAR(120) DEFAULT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    amount DECIMAL(10, 2) NOT NULL,
    payment_method VARCHAR(50) DEFAULT NULL,
    signature_verified BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason VARCHAR(500) DEFAULT NULL,
    audit_payload TEXT DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_pt_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT uq_pt_reference UNIQUE (transaction_reference),
    CONSTRAINT uq_pt_idempotency UNIQUE (idempotency_key),
    INDEX idx_pt_booking (booking_id),
    INDEX idx_pt_gateway_ref (gateway_reference),
    INDEX idx_pt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. Payment Webhook Events Table
CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(120) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL,
    signature VARCHAR(255) DEFAULT NULL,
    raw_payload MEDIUMTEXT NOT NULL,
    received_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_pwe_event_id UNIQUE (event_id),
    INDEX idx_pwe_provider (provider),
    INDEX idx_pwe_received (received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

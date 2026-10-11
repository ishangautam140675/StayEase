# StayEase Production Operations Runbook

Comprehensive operational runbook, disaster recovery guide, and incident response manual for the **StayEase** hospitality platform.

---

## 1. System Topology & Architecture

StayEase is an enterprise Spring Boot 3 / Java 21 platform backed by MySQL 8:

```
[ Client / Browser / Mobile ]
            │
            ▼
    [ Nginx / Reverse Proxy ] (SSL Termination, Rate Limiting, Security Headers)
            │
            ▼
    [ Spring Boot 3 Application (Port 8080) ]
        ├── Embedded Tomcat 10.1 (HTTP/1.1 NIO)
        ├── Spring Security 6 (BCrypt, CSRF, RBAC Filter Chains)
        ├── Spring Data JPA / Hibernate 6 (Pessimistic Locking)
        ├── ThreadPoolTaskExecutor (stayease-async- 5-20 threads, Queue: 500)
        ├── Scheduled Reminders Engine (BookingReminderService @ 9:00 AM)
        └── Spring Boot Actuator (Probes: /actuator/health, /actuator/info)
            │
            ├── MySQL 8.0+ Database (HikariCP Pool: 10 connections)
            ├── Razorpay Payment Gateway (Webhook integration + HMAC SHA256)
            └── SMTP / Transactional Mail Relay (Outbox idempotency logging)
```

---

## 2. Pre-Flight Verification & Health Checks

### 2.1 Live Health Probes
StayEase exposes health indicators configured with strict security boundaries:

```bash
# Public Liveness & Database Readiness Check
curl -s http://localhost:8080/actuator/health
```

Expected Output:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

*Note: In production profile (`spring.profiles.active=prod`), database and disk space internal details are hidden from public callers (`management.endpoint.health.show-details=never`).*

### 2.2 Production Environment Validator
When booted with `SPRING_PROFILES_ACTIVE=prod`, [`ProductionEnvironmentValidator.java`](file:///d:/Projects/StayEase/src/main/java/com/stayease/config/ProductionEnvironmentValidator.java) performs strict pre-flight assertions before opening the HTTP port:
1. `SPRING_DATASOURCE_PASSWORD` must be set and must not equal default dev passwords.
2. `APP_ADMIN_PASSWORD` must meet production complexity (min 8 chars, mixed case, digit, symbol).
3. If `APP_MAIL_ENABLED=true`, `SPRING_MAIL_HOST` must be explicitly configured.
4. If `APP_PAYMENT_PROVIDER=razorpay`, `APP_PAYMENT_RAZORPAY_KEY_ID` and `APP_PAYMENT_RAZORPAY_KEY_SECRET` must be set.

---

## 3. Database Administration & Migrations

### 3.1 Initial Database Setup
```sql
CREATE DATABASE IF NOT EXISTS stayease
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

Apply the production schema DDL located at [`scripts/schema-prod.sql`](file:///d:/Projects/StayEase/scripts/schema-prod.sql):
```bash
# Windows PowerShell
Get-Content scripts/schema-prod.sql | mysql -u root -p stayease

# Linux / Bash
mysql -u root -p stayease < scripts/schema-prod.sql
```

### 3.2 Automated Backup Procedures
Automated backup scripts are located in `scripts/`:
- **Windows PowerShell**: `scripts/backup-db.ps1`
- **Linux / Docker**: `scripts/backup-db.sh`

```bash
# Run database backup (creates gzip archive in backups/)
powershell -File scripts/backup-db.ps1 -Database stayease -BackupDir C:\Backups\StayEase
```

### 3.3 Database Restore & Disaster Recovery
```bash
# Restore from backup archive
powershell -File scripts/restore-db.ps1 -BackupFile C:\Backups\StayEase\stayease-backup-20261012.sql.gz
```

---

## 4. Operational Procedures

### 4.1 Room Inventory & Maintenance Blackouts
1. **Adding/Editing Rooms**: Navigate to `/admin/rooms`. Every room has a unique room number, type, capacity, price per night, and amenity list.
2. **Scheduling Maintenance**: Navigate to `/admin/operations/maintenance`.
   - Prevent guest bookings during repairs or fumigation.
   - If a maintenance block overlaps with an approved booking, the system will prevent scheduling and alert the administrator with the conflicting booking reference.

### 4.2 Seasonal Pricing Rules
1. Navigate to `/admin/operations/pricing`.
2. Configure date ranges with a fixed price per night or a percentage multiplier (e.g., `1.25` for a 25% peak-season surcharge).
3. Active rules automatically calculate booking totals in real time on both customer and administrative reservation screens.

### 4.3 Check-in, Check-out & No-Show Workflows
1. **Guest Web Check-In**: Guests can confirm check-in on the arrival date from their customer dashboard (`/customer/bookings`).
2. **Admin Operational Check-In**: Admin can mark check-in (`/admin/bookings/{id}/check-in`) or check-out (`/admin/bookings/{id}/check-out`).
3. **No-Show Handling**: If a guest fails to arrive, admin executes **Mark No-Show** (`/admin/bookings/{id}/no-show`), freeing the room for inventory, logging an audit trail entry, and notifying the guest.

### 4.4 Financial Reports & Audit Log Exports
1. **Financial & Operational Analytics**:
   - Access `/admin/dashboard` to inspect live **ADR (Average Daily Rate)**, **RevPAR (Revenue Per Available Room)**, **Average Lead Time**, and **Occupancy Rates**.
   - Click **CSV** to download `stayease-analytics-[start]-to-[end].csv`.
2. **Compliance Audit Trail**:
   - Access `/admin/operations/audit-logs`.
   - Filter by administrator email, action type, or date range.
   - Click **Export CSV** to download `stayease-audit-logs-[date].csv`.

---

## 5. Incident Response & Troubleshooting

### 5.1 High Concurrency & Room Overbooking Attempts
* **Symptom**: Multiple guests attempt booking the same room for overlapping dates simultaneously.
* **Mechanism**: StayEase applies `PESSIMISTIC_WRITE` locks on the target room and evaluates `(b.checkIn < checkOut) AND (b.checkOut > checkIn)`.
* **Resolution**: The first transaction succeeds; the competing transaction throws `RoomNotAvailableException` and safely rolls back without inconsistent state. The user is presented with alternative available rooms of the same capacity.

### 5.2 Payment Gateway Webhook Failures
* **Symptom**: Webhook returns 400 Bad Request or `InvalidSignatureException`.
* **Investigation**:
  1. Inspect `payment_webhook_events` table for the event record.
  2. Verify that `APP_PAYMENT_RAZORPAY_WEBHOOK_SECRET` matches the secret registered in the Razorpay dashboard.
  3. Verify HMAC SHA-256 calculation.
* **Fallback**: Administrators can inspect transactions on `/admin/bookings` and trigger manual payment status verification (`/customer/bookings/{id}/verify-payment`).

### 5.3 Asynchronous Queue Backpressure or Email Outage
* **Symptom**: Slow email dispatch or external SMTP failures.
* **Behavior**:
  - Email and notification methods run asynchronously on the `taskExecutor` pool (`stayease-async-`, 5 core, 20 max threads, queue capacity 500).
  - Outbox deliveries are wrapped in isolated transactions (`REQUIRES_NEW`).
  - Delivery failures are logged to `notification_logs` with status `FAILED` and error details without disrupting core booking or payment transactions.

---

## 6. Log Inspection & Rotation

StayEase utilizes Logback with daily gzip archiving and size-based rolling:
- Active log file: `logs/stayease.log`
- Rolled archives: `logs/stayease-%d{yyyy-MM-dd}.%i.log.gz`
- File limit: `50 MB` per file, total cap `2 GB`.

```bash
# Tail live application log
Get-Content -Path logs/stayease.log -Wait -Tail 100
```

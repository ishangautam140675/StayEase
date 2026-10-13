# StayEase — Production-Grade Hospitality & Reservation Management Platform

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/Tests-295%20Passing-success.svg)](file:///d:/Projects/StayEase/src/test/)
[![License](https://img.shields.io/badge/License-Proprietary-blue.svg)]()

**StayEase** is a comprehensive, production-grade **Hostel, PG & Boutique Hotel Reservation Management Platform** engineered with modern Java 21, Spring Boot 3, Spring Security 6, Spring Data JPA / Hibernate 6, Thymeleaf, and MySQL 8.

Designed from the ground up for high concurrency, zero-overbooking guarantees, and multi-tenant operational resilience, StayEase provides an editorial self-service booking portal for guests alongside an administrative management console for property operators.

---

## 1. Key Architectural Highlights

* **Pessimistic Concurrency & Zero-Overbooking**: Enforces `PESSIMISTIC_WRITE` database row locks (`SELECT ... FOR UPDATE`) during room availability checks and reservation creation, completely preventing double-bookings even under concurrent flash-booking traffic.
* **Mathematical Date Overlap Validation**: Employs the canonical booking interval overlap condition `(existing.checkIn < new.checkOut) AND (existing.checkOut > new.checkIn)`. Back-to-back stays are natively supported (a new guest checks in at 2:00 PM on the same date a departing guest checks out at 11:00 AM).
* **Role-Differentiated Portals**: Strict separation of concerns between `ROLE_CUSTOMER` and `ROLE_ADMIN` with dedicated navigation, controller pipelines, and layout templates.
* **Real-Time In-App & Asynchronous Notifications**: Background task execution configured via dedicated `ThreadPoolTaskExecutor` (`stayease-async-`, 5 core, 20 max threads, 500 capacity queue) ensuring non-blocking delivery for transactional emails and in-app alerts.
* **Scheduled Reminders Engine**: Automated `@Scheduled` cron job (`BookingReminderService`) dispatching personalized check-in alerts to guests whose stays begin the next day.
* **Payment Gateway Architecture**: Pluggable payment provider abstraction supporting Razorpay (HMAC SHA-256 webhook cryptographic verification) and zero-friction Sandbox mode with idempotency keys.
* **Hospitality Industry Financial Analytics**: Built-in calculation of **ADR (Average Daily Rate)**, **RevPAR (Revenue Per Available Room)**, **Average Booking Lead Time**, and room-by-room utilization rates.
* **Compliance Audit Trail**: Immutable logging of all administrative operations (approvals, rejections, check-ins, check-outs, no-shows, rate updates, and maintenance schedules) with CSV export.
* **Production Hardening**: Security response headers (HSTS, CSP, X-Frame-Options: DENY, nosniff), Spring Boot Actuator health probes, HikariCP connection pooling, size-and-time based log rotation, and strict production environment pre-flight validation.
* **100% Test Pass Rate**: **295 automated tests** across unit, integration, concurrency, security, and UI regression suites with 0 failures.

---

## 2. Technology Stack

| Layer | Technology | Description |
|---|---|---|
| **Runtime & Language** | Java 21 LTS | Record classes, pattern matching, text blocks |
| **Framework** | Spring Boot 3.3.4 | Core framework, dependency injection, auto-configuration |
| **Security** | Spring Security 6 | BCrypt hashing, CSRF tokens, session fixation protection, RBAC |
| **Persistence & ORM** | Spring Data JPA / Hibernate 6 | Repositories, pessimistic locking, transactions, JPQL |
| **Database** | MySQL 8.0+ | Relational schema, ACID compliance, UTF-8 (utf8mb4) |
| **Connection Pool** | HikariCP | High-performance pooled database connections |
| **View Layer** | Thymeleaf 3 | Server-side template engine, fragment composition |
| **Frontend Styling** | Bootstrap 5.3.3 + Bootstrap Icons | Mobile-responsive layout, editorial hospitality typography |
| **Monitoring** | Spring Boot Actuator | Production health probes (`/actuator/health`, `/actuator/info`) |
| **Task Execution** | Spring `@EnableAsync` + `@EnableScheduling` | ThreadPoolTaskExecutor, background reminders |
| **Testing** | JUnit 5, MockMvc, AssertJ, H2 | Comprehensive automated testing (295 tests) |

---

## 3. System Architecture & Entity Model

```
                    ┌───────────────────────────┐
                    │           User            │
                    │  (Customer / Admin Roles) │
                    └─────────────┬─────────────┘
                                  │ 1:N
        ┌─────────────────────────┼─────────────────────────┐
        ▼                         ▼                         ▼
┌──────────────┐          ┌──────────────┐          ┌──────────────┐
│ InAppNotif   │          │   Booking    │          │ AdminAuditLog│
└──────────────┘          └───────┬──────┘          └──────────────┘
                                  │ N:1
                                  ▼
                          ┌──────────────┐
                          │     Room     │
                          └───────┬──────┘
                                  │ 1:N
        ┌─────────────────────────┴─────────────────────────┐
        ▼                                                   ▼
┌─────────────────────────┐               ┌─────────────────────────┐
│ RoomMaintenanceSchedule │               │    SeasonalPriceRule    │
└─────────────────────────┘               └─────────────────────────┘
```

---

## 4. Visual Interface Showcase

| 🌟 1. Landing Page (`/`) | 🗺️ 2. Worldwide Map Radar (`/discovery`) |
|:---:|:---:|
| <img src="docs/screenshots/01_landing_page.png" width="450" alt="Landing Page"/> | <img src="docs/screenshots/02_worldwide_discovery_map.png" width="450" alt="Discovery Map"/> |
| **3. Room Catalog & Filters (`/rooms`)** | **4. Room Details & Live Booking (`/rooms/1`)** |
| <img src="docs/screenshots/03_room_catalog.png" width="450" alt="Room Catalog"/> | <img src="docs/screenshots/04_room_details.png" width="450" alt="Room Details"/> |
| **5. Customer Dashboard (`/customer/dashboard`)** | **6. Admin Analytics Console (`/admin/dashboard`)** |
| <img src="docs/screenshots/05_customer_dashboard.png" width="450" alt="Customer Dashboard"/> | <img src="docs/screenshots/06_admin_operations_dashboard.png" width="450" alt="Admin Dashboard"/> |

> 📷 *Explore the complete gallery and route walkthrough in [**docs/SCREENSHOTS.md**](docs/SCREENSHOTS.md).*

---

## 5. Features by Persona

### 🌐 Public & Anonymous Guests
* **Landing Page (`/`)**: Hero destination search, amenities showcase, community reviews, and real-time room availability preview.
* **Room Catalog (`/rooms`)**: Filter by room type (`SINGLE`, `DOUBLE`, `TRIPLE`, `DORMITORY`), capacity, price range, and date availability.
* **Room Details (`/rooms/{id}`)**: High-resolution imagery, amenity badges, price breakdowns, and one-click booking CTA.
* **Self-Service Authentication**: User registration with strong password rules and form-based login with role-aware routing.

### 👤 Customer Experience (`ROLE_CUSTOMER`)
* **Personalized Dashboard (`/customer/dashboard`)**: Key stay metrics, spotlight card for the next upcoming stay, and quick links.
* **Reservation Booking (`/customer/book/{id}`)**: Live dynamic price computation, date overlap checks, and smart fallback suggestions if full.
* **Booking Registry (`/customer/bookings`)**: Paginated booking history with status badges (`PENDING`, `APPROVED`, `CHECKED_IN`, `CHECKED_OUT`, `CANCELLED`, `NO_SHOW`).
* **Web Check-In**: Customers can self-confirm arrival on the check-in day directly from the web portal.
* **Digital Receipts (`/customer/bookings/{id}/receipt`)**: Formal printable guest booking vouchers with verification stamps.
* **Account Settings (`/customer/profile`)**: Profile editing, password change, and email verification status.
* **Real-Time Notification Bell**: Unread counter badge and dropdown alerts with periodic background polling.

### 🛡️ Property Operations & Administration (`ROLE_ADMIN`)
* **Administrative Console (`/admin/dashboard`)**:
  * Live inventory ribbon: Total rooms, active rooms, occupied rooms today, vacant rooms today.
  * Hospitality financial KPIs: **ADR**, **RevPAR**, **Average Lead Time**, **Occupancy Rate**, and **Window Revenue**.
  * Dynamic date-range filter for historical performance review.
  * **Export Analytics CSV**: Download financial and room utilization breakdown reports.
* **Reservation Review & Management (`/admin/bookings`)**:
  * Unified status tabs with live counters.
  * Single-click approval with transactional date verification.
  * Rejection with mandatory remark dialog and notification dispatch.
  * Operational check-in, check-out, and no-show actions with audit log recording.
* **Room Inventory Control (`/admin/rooms`)**:
  * Full CRUD operations, active status toggling, and soft-delete safeguards.
* **Maintenance Blackouts (`/admin/operations/maintenance`)**:
  * Schedule room maintenance intervals to block booking availability; prevents blackout scheduling over active bookings.
* **Dynamic Pricing Engine (`/admin/operations/pricing`)**:
  * Seasonal pricing rules with custom date ranges, fixed night prices, or percentage multipliers.
* **Compliance Audit Trail (`/admin/operations/audit-logs`)**:
  * Complete activity log with email, action, and timestamp filters.
  * **Export Audit CSV**: One-click download of compliance history.

---

## 6. Quick Start Guide

### Prerequisites
* **Java Development Kit (JDK)**: Version 21 LTS installed (`java -version`).
* **MySQL Database Server**: Version 8.0+ running locally (`mysqladmin ping`).
* **Maven**: Version 3.8+ (or use bundled `./mvnw` wrapper).

### Step 1: Clone & Configure Database
```bash
git clone https://github.com/ishangautam140675/StayEase.git
cd StayEase

# Create the MySQL database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS stayease CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### Step 2: Run Automated Tests
Verify all 295 tests execute and pass:
```bash
mvn clean test
```

### Step 3: Run the Application Locally
```bash
# Using Spring Boot Maven plugin
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.password=your_mysql_password"

# Or package and execute the JAR
mvn clean package -DskipTests
java -jar target/stayease-0.0.1-SNAPSHOT.jar --spring.datasource.password=your_mysql_password
```

Access the application in your browser:
* Web Portal: `http://localhost:8080`
* Actuator Health: `http://localhost:8080/actuator/health`

### Seed Credentials
StayEase automatically provisions default seed accounts upon first launch:
* **Administrator**: `admin@stayease.com` / `Admin@1234`
* **Sample Guest**: `customer@stayease.com` / `Customer@1234`

---

## 7. Docker Containerization

StayEase includes multi-stage containerization with Docker and Docker Compose:

```bash
# Build and start StayEase with dedicated MySQL container
docker-compose up --build -d
```

Services started:
* `stayease-app`: Spring Boot application on port `8080`
* `stayease-db`: MySQL 8.0 on port `3306` with persistent volume `stayease-mysql-data`

---

## 8. Configuration Reference (12-Factor Variables)

| Variable | Description | Default (Dev) | Production Required |
|---|---|---|:---:|
| `SPRING_DATASOURCE_URL` | JDBC URL for MySQL | `jdbc:mysql://localhost:3306/stayease?...` | Yes |
| `SPRING_DATASOURCE_USERNAME` | Database username | `root` | Yes |
| `SPRING_DATASOURCE_PASSWORD` | Database password | — | **Yes** |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `dev` | Recommended (`prod`) |
| `APP_ADMIN_EMAIL` | Default admin email | `admin@stayease.com` | Yes |
| `APP_ADMIN_PASSWORD` | Default admin password | `Admin@1234` | **Yes (min 8 chars, strong)** |
| `APP_BASE_URL` | Public application URL | `http://localhost:8080` | Yes |
| `APP_MAIL_ENABLED` | Enable outbound SMTP | `false` (simulation mode) | Optional |
| `APP_PAYMENT_PROVIDER` | Payment provider | `sandbox` | Optional (`razorpay`) |

---

## 9. Documentation & Runbooks

* [**Visual Interface Showcase (`docs/SCREENSHOTS.md`)**](docs/SCREENSHOTS.md): High-resolution screenshots of the landing page, worldwide map radar, room details, and admin operations console.
* [**Software Requirements Specification (`docs/SRS.md`)**](docs/SRS.md): Official SRS document with high-resolution visual scans (Pages 1–3), functional/non-functional requirements, and business rules.
* [**Operations Runbook (`docs/RUNBOOK.md`)**](docs/RUNBOOK.md): Pre-flight verification, database migrations, backup/restore scripts, logging rotation, and incident response procedures.
* [**API & Integration Guide (`docs/API_GUIDE.md`)**](docs/API_GUIDE.md): Catalog of notification polling, webhook callbacks, actuator endpoints, and CSV export specifications.
* [**Testing Checklist (`docs/TEST-CHECKLIST.md`)**](docs/TEST-CHECKLIST.md): Verification matrices across authentication, booking flows, and concurrency.

---

## 10. License

Proprietary — Developed for StayEase Hospitality Management. All rights reserved.

# StayEase

A modern, production-grade **Hostel & PG Booking Management System** engineered with Java 21, Spring Boot 3, Spring Data JPA, Spring Security, Thymeleaf, and MySQL. StayEase delivers a seamless self-service booking journey for guests and an end-to-end administration portal for property managers, featuring pessimistic locking concurrency control, mathematical date overlap detection, and responsive design.

---

## 1. Overview & Key Highlights

* **Role-Differentiated Portals**: Completely isolated experiences and tailored navigation bars for `CUSTOMER` vs `ADMIN` roles.
* **Concurrency-Hardened Reservations**: Prevents double-bookings under concurrent requests using `PESSIMISTIC_WRITE` database row locks (`SELECT ... FOR UPDATE`).
* **Strict Date Overlap Rule**: Employs the canonical mathematical booking overlap condition `(existing.checkIn < new.checkOut) AND (existing.checkOut > new.checkIn)`, permitting zero-waste back-to-back stays where one guest departs on the exact check-in day of the next guest.
* **Intelligent Alternative Suggestions**: Automatically suggests up to 3 non-conflicting rooms of the same capacity if a desired room is unavailable.
* **Spring Data Pageable Pagination**: Zero-crash pagination (9 rooms/page, 10 bookings/page) preserving active filters across pages.
* **CSV Reporting with Formula Injection Mitigation**: Generates administrative booking reports with RFC-4180 escaping and formula sanitization.
* **Robust Test Coverage**: 178 unit, integration, and security matrix tests passing with zero failures.

---

## 2. Features by Role

### 🌐 Public & Anonymous Guests
* **Landing Page**: Editorial hospitality design with brand aesthetics, hero search widget, and room showcases.
* **Public Room Catalog (`/rooms`)**: Filterable by room type, guest capacity, and price range with paginated 9-room grid view.
* **Room Details (`/rooms/{id}`)**: Full amenity inspection, pricing breakdown, and direct booking CTA.
* **Authentication**: Self-service user registration with strong password policies and form login with role-aware redirection.

### 👤 Customer Features (`ROLE_CUSTOMER`)
* **Customer Dashboard (`/customer/dashboard`)**:
  * Personal stay metrics (upcoming, pending, past stays).
  * Prominent hero spotlight for the next upcoming stay with nights countdown.
  * Real-time Room Availability Search widget (`/customer/availability`).
* **Booking Reservation (`/customer/book/{roomId}`)**:
  * Real-time price calculation (`pricePerNight * nights`).
  * Instant date overlap detection with friendly error notices and alternative room recommendations.
* **Booking Management (`/customer/bookings`)**:
  * Paginated table (10 bookings/page) with unified status badges (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`).
  * Rejection remarks visibility for transparency.
  * Self-service cancellation with confirmation dialogs for pending and future approved stays.

### 🛡️ Admin Features (`ROLE_ADMIN`)
* **Admin Dashboard (`/admin/dashboard`)**:
  * Real-time capacity stats: Total rooms, active rooms, occupied rooms today, vacant rooms today, and occupancy percentage.
  * 6-Month booking trend chart and metrics.
  * Urgent pending approvals queue with quick review actions.
* **Room Management (`/admin/rooms`)**:
  * Full CRUD control (add, edit, toggle active status, soft delete).
  * Delete confirmation modal preventing accidental removal.
* **Booking Review & Approvals (`/admin/bookings`)**:
  * Tabbed status filters (`All`, `Pending`, `Approved`, `Rejected`, `Cancelled`) with live counts.
  * Paginated table (10 per page) with status badges and table responsiveness.
  * Inline single-click booking approvals with transactional overlap re-verification.
  * Inline rejections with mandatory administrative reason remarks and confirmation modal.
  * **Export CSV**: One-click download (`bookings-YYYY-MM-DD.csv`) honoring active status filters with formula injection protection.

---

## 3. Technology Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Language** | Java 21 (LTS) | Modern Java language features, virtual threads ready |
| **Framework** | Spring Boot 3.3.4 | Core enterprise runtime, auto-configuration, dependency injection |
| **Web & Templates**| Spring MVC + Thymeleaf 3 | Server-side HTML rendering with fragment composition |
| **Styling & Icons** | Bootstrap 5.3.3 + Bootstrap Icons + Custom CSS | Responsive layout, mobile-first design, Editorial hospitality theme |
| **Database** | MySQL 8.0+ | Relational persistence with ACID transactions and row locking |
| **ORM / Persistence**| Spring Data JPA / Hibernate 6 | Repository abstractions, custom JPQL queries, eager joins |
| **Security** | Spring Security 6 | BCrypt password hashing, session management, CSRF tokens, RBAC |
| **Validation** | Jakarta Bean Validation | Comprehensive DTO constraint validation |
| **Testing** | JUnit 5 + MockMvc + AssertJ + H2 | Comprehensive automated testing (178 tests) |

---

## 4. Prerequisites

Before running StayEase locally, ensure you have:
* **Java Development Kit (JDK)**: Version 21 or later installed (`java -version`).
* **MySQL Database Server**: Version 8.0 or later running locally (`mysqladmin ping`).
* **Git**: Installed and available in your shell.

---

## 5. MySQL Database Setup

1. Start your local MySQL service (e.g. `net start MySQL80` on Windows, or `sudo systemctl start mysql` on Linux/macOS).
2. Log into MySQL client and create the database (or allow Spring Boot's JDBC flag `createDatabaseIfNotExist=true` to create it):
```sql
CREATE DATABASE IF NOT EXISTS stayease CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 6. Environment Variables

StayEase strictly adheres to 12-factor application design: **no passwords or secrets are hardcoded in the codebase**. Configure the following environment variables:

| Variable | Description | Required | Default / Example Value |
|---|---|---|---|
| `DB_URL` | JDBC Connection URL for MySQL | Optional | `jdbc:mysql://localhost:3306/stayease?createDatabaseIfNotExist=true&serverTimezone=UTC` |
| `DB_USERNAME` | MySQL database user | Optional | `root` |
| `DB_PASSWORD` | MySQL user password | **Yes** | `your_mysql_password` |
| `ADMIN_EMAIL` | Email for bootstrap Admin seed user | Optional | `admin@stayease.com` |
| `ADMIN_PASSWORD` | Password for bootstrap Admin seed user | Optional | `Admin@1234` |

---

## 7. How to Run the App and the Tests

### Setting Environment Variables & Starting Application

#### On Windows (PowerShell):
```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/stayease?createDatabaseIfNotExist=true&serverTimezone=UTC"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
$env:ADMIN_EMAIL="admin@stayease.com"
$env:ADMIN_PASSWORD="Admin@1234"

./mvnw.cmd spring-boot:run
```

#### On Linux / macOS (Bash):
```bash
export DB_URL="jdbc:mysql://localhost:3306/stayease?createDatabaseIfNotExist=true&serverTimezone=UTC"
export DB_USERNAME="root"
export DB_PASSWORD="your_mysql_password"
export ADMIN_EMAIL="admin@stayease.com"
export ADMIN_PASSWORD="Admin@1234"

./mvnw spring-boot:run
```

Once started, open your web browser at:
👉 **[http://localhost:8080](http://localhost:8080)**

* If `ADMIN_EMAIL` and `ADMIN_PASSWORD` are provided, the `DataSeeder` automatically initializes the admin account and populates 8 sample curated rooms.
* You can log in immediately as Admin (`admin@stayease.com` / `Admin@1234`) or register a customer account at `/register`.

### Running Automated Tests
The automated test suite runs against an isolated, in-memory H2 database with MySQL compatibility mode (`MODE=MySQL`). **No local MySQL server is required to run tests.**

```bash
# Windows
./mvnw.cmd clean test

# Linux / macOS
./mvnw clean test
```

---

## 8. Package Structure

```
com.stayease
├── config/                  # SecurityConfig, DataSeeder, Authentication handlers
├── controller/              # Spring MVC Controllers (Auth, Room, Customer, Admin, etc.)
├── dto/                     # Form backing DTOs (RegisterForm, RoomForm, BookingForm)
├── exception/               # Custom domain exceptions & GlobalExceptionHandler
├── model/                   # JPA Entities (User, Room, Booking) & Enums (Role, RoomType, BookingStatus)
├── repository/              # Spring Data JPA Repositories with custom JPQL queries
└── service/                 # Core business services (AuthService, RoomService, BookingService,
                             # DashboardService, BookingExportService)
```

---

## 9. Security Design

* **BCrypt Hashing**: All passwords (customer registrations and admin seeds) are securely hashed using BCrypt with automatic salting before persistence. Plaintext passwords never enter logs or database tables.
* **Role-Based Access Control (RBAC)**:
  * Public routes: `/`, `/rooms/**`, `/register`, `/login`, static assets, and `/error/**`.
  * Customer-only routes: `/customer/**` (`hasRole('CUSTOMER')`).
  * Admin-only routes: `/admin/**` (`hasRole('ADMIN')`).
* **CSRF Protection**: Synchronizer Token Pattern is enabled across all state-changing HTTP `POST` requests. Form fragments inject `<input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}" />`.
* **Session Security**: Session fixation mitigation (`changeSessionId()`), 30-minute inactivity timeout, and `HttpOnly` cookie flags preventing client-side script inspection.
* **Double-Submit Prevention**: Client-side form handlers disable submit buttons immediately upon submission dispatch to eliminate duplicate double clicks.

---

## 10. Booking Rules & Overlap Formula

### Date Overlap Logic
Two bookings for the same room overlap if and only if:
$$\text{Existing.CheckIn} < \text{New.CheckOut} \quad \text{AND} \quad \text{Existing.CheckOut} > \text{New.CheckIn}$$

Only bookings in **blocking statuses** (`PENDING` or `APPROVED`) cause conflicts. Rejected or cancelled bookings do not block dates.

#### Why Touching Dates Are Allowed
If Guest A departs on **2026-11-05** (`checkOut = 2026-11-05`) and Guest B arrives on **2026-11-05** (`checkIn = 2026-11-05`):
* `Existing.CheckOut (2026-11-05) > New.CheckIn (2026-11-05)` evaluates to **false**.
* Therefore, the condition is false, meaning **no conflict exists**.
* This matches hotel and PG industry standard: check-out occurs at midday and check-in occurs in the afternoon on the same date.

### Transactional Concurrency Control
When creating a booking or approving a pending reservation:
1. The transaction acquires a **pessimistic write lock** on the target room row:
   ```sql
   SELECT r FROM Room r WHERE r.id = :id FOR UPDATE
   ```
2. The overlap check executes inside the locked transaction.
3. If valid, the new booking is saved, and the lock is released upon commit.
4. Concurrent transactions attempting to book the same room are forced to queue, preventing race conditions.

---

## 11. Screenshots

| Section | Preview |
|---|---|
| **Landing Page** | *Editorial hospitality hero section with bold color accents and quick search* |
| **Accommodations Catalog** | *Responsive 9-room grid with badge amenities, price tags, and pagination* |
| **Customer Dashboard** | *Next stay spotlight, upcoming bookings table, and quick availability search* |
| **Booking Reservation Form** | *Live price calculation, date range picker, and instant alternative room suggestions* |
| **Admin Dashboard** | *Room capacity metrics, 6-month booking trends, and pending approval queue* |
| **Manage Bookings** | *Status tabs, responsive bookings table, approval buttons, and CSV export* |

*(Screenshots can be added to the `/docs/screenshots/` directory for visual reference).*

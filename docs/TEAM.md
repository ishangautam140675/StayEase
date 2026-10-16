# StayEase — Project Engineering Team & Role Ownership

**Institution:** Rajasthan Technical University (RTU), Kota  
**Department:** Computer Science & Engineering  
**Project:** StayEase — Hospitality & Reservation Management Platform  

---

## 👥 Engineering Team & Contributions Summary

| S.No. | Name | RTU Roll No. | Assigned Role | Primary Focus & Domain Ownership |
|:---:|:---|:---:|:---|:---|
| 1 | **Ishan Gautam** *(Team Leader)* | `24EARCS065` | **Core Developer** | Architecture, Concurrency & Locking, Payment Integration, Spring Security, Async Engine |
| 2 | **Manisha Sharma** | `24EARCS089` | **Database Manager & Tester** | Relational Schema, Indexes, Foreign Keys, HikariCP, Data Seeder, `@DataJpaTest` Suites |
| 3 | **Pragati Jain** | `24EARCS113` | **Documentation Lead** | SRS Specification, UML Lifecycle, Operations Runbook, API Guides, Viva Prep Material |
| 4 | **Prince Pariyani** | `24EARCS120` | **UI/UX & Testing Lead** | Design System, Thymeleaf Views, Geospatial Radar, Responsive Layouts, Cross-Browser Tests |

---

## 🛠️ Detailed Role Responsibilities & Work Accomplished

### 1. Ishan Gautam (Team Leader)
* **RTU Roll Number:** `24EARCS065`
* **Assigned Role:** Core Developer
* **Key Contributions & Deliverables:**
  * **System Architecture & Framework Orchestration:** Designed and initialized the enterprise multi-tier architecture using Java 21 LTS and Spring Boot 3.3.4, enforcing strict separation of concerns across Controller, Service, and Repository layers.
  * **Zero-Overbooking Concurrency Engine:** Formulated and implemented `PESSIMISTIC_WRITE` row-level locks (`SELECT ... FOR UPDATE`) in `BookingRepository` to guarantee data consistency and completely prevent double-bookings during concurrent reservation requests.
  * **Mathematical Date Overlap Algorithm:** Implemented canonical booking collision detection `(checkIn < newCheckOut AND checkOut > newCheckIn)` with support for seamless back-to-back stays.
  * **Pluggable Payment Gateway Architecture:** Engineered the payment provider interface with Razorpay cryptographic HMAC-SHA256 signature verification and sandbox idempotency handling (`PaymentService`, `PaymentProvider`).
  * **Role-Based Access Control (RBAC):** Configured Spring Security 6 filter chains, BCrypt password hashing, session fixation safeguards, and role-specific redirection (`ROLE_ADMIN` vs `ROLE_CUSTOMER`).
  * **Asynchronous Notifications & Scheduling:** Built background event execution using `ThreadPoolTaskExecutor` for non-blocking email alerts and configured `@Scheduled` cron jobs (`BookingReminderService`) for automated guest arrival notifications.

---

### 2. Manisha Sharma
* **RTU Roll Number:** `24EARCS089`
* **Assigned Role:** Database Manager and Tester
* **Key Contributions & Deliverables:**
  * **Relational Schema Modeling & Normalization:** Designed the 3NF relational schema in MySQL 8 for core entities including `User`, `Room`, `Booking`, `RoomMaintenanceSchedule`, `SeasonalPriceRule`, `AdminAuditLog`, and `InAppNotification`.
  * **Indexing & Performance Tuning:** Configured composite database indexes (such as `idx_bookings_room_dates` on `(room_id, check_in, check_out)`) for sub-millisecond query execution during real-time availability scans.
  * **Data Integrity & Referential Constraints:** Defined foreign key cascades, unique room number constraints, non-null validations, and UTF-8 (`utf8mb4`) multi-byte character collation.
  * **Connection Pooling Optimization:** Tuned HikariCP connection pool configurations (`maximum-pool-size: 10`, `minimum-idle: 5`, `connection-timeout: 20000ms`) for high-concurrency throughput.
  * **Database Testing & Test Automation:** Authored and verified `@DataJpaTest` suites in `RepositoryTests.java`, testing duplicate email constraints, room uniqueness, and ACID transaction rollbacks under concurrent operations.

---

### 3. Pragati Jain
* **RTU Roll Number:** `24EARCS113`
* **Assigned Role:** Documentation Lead
* **Key Contributions & Deliverables:**
  * **Software Requirements Specification (SRS):** Authored the comprehensive SRS document (`docs/SRS.md`) covering functional requirements, non-functional requirements, data dictionaries, and operational constraints.
  * **Project Lifecycle & UML Modeling:** Documented system use case diagrams, entity-relationship diagrams, state transition charts for booking lifecycles, and sequence diagrams.
  * **Operational Runbooks & API Documentation:** Formulated the Production Runbook (`docs/RUNBOOK.md`), API Integration Specifications (`docs/API_GUIDE.md`), and Testing Checklist (`docs/TEST-CHECKLIST.md`).
  * **Academic Milestone Tracking:** Compiled weekly project deliverables and progress logs spanning Weeks 1 through 9 (`StayEase_Academic_Project_Lifecycle_Weeks_1_to_9.pdf`).
  * **Viva & Project Defense Guides:** Prepared comprehensive technical defense guides and viva voce question banks covering system architecture, security models, and concurrency edge cases.

---

### 4. Prince Pariyani
* **RTU Roll Number:** `24EARCS120`
* **Assigned Role:** UI/UX and Testing Lead
* **Key Contributions & Deliverables:**
  * **Hospitality Design System:** Created and implemented the custom **Editorial Hospitality × Bold Color Blocking** design language using custom CSS tokens (`app.css`), DM Sans typography, and curated color palettes.
  * **Thymeleaf Template Engineering:** Developed modular Thymeleaf layout fragments, dynamic navigation bars, responsive card grids, reservation summary sidebars, and status badges.
  * **Interactive Geospatial Radar:** Integrated OpenStreetMap with Leaflet.js on `/discovery`, featuring 220ms debounced typeahead search, radius filtering (5 km to 100 km), and HTML5 Geolocation API device detection.
  * **Customer & Admin Experience Flows:** Built intuitive user journeys for self-service room discovery, dynamic live price breakdowns, web check-in, and printable formal booking receipts (`booking-receipt.html`).
  * **Interface & Cross-Browser Testing:** Conducted visual regression, accessibility (a11y) color contrast checks, mobile viewport responsiveness audits, and client-side form validation tests.

---

*StayEase — Academic Capstone Project, Rajasthan Technical University (RTU).*

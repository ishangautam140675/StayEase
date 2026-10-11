# StayEase — Conversation & Development Summary

**Date:** October 10, 2026  
**Repository:** [https://github.com/ishangautam140675/StayEase](https://github.com/ishangautam140675/StayEase)  
**Branch:** `main`

---

## 1. Executive Summary

This conversation covered the successful implementation and verification of **Phase 2** (Domain Entities, Enums, and Repositories), the establishment of the **Permanent UI Design System (Editorial Hospitality × Bold Color Blocking)**, and **Phase 3** (Shared Layout, Role-based Navbar, and Error Handling). All 11 unit and integration tests are passing (`./mvnw test`), the live MySQL database schema was verified, and all code was pushed to the GitHub repository.

---

## 2. Phase 2: Entities, Enums, and Repositories

### 2.1 Enums (`com.stayease.model`)
* **[`Role.java`](src/main/java/com/stayease/model/Role.java):**
  * `ADMIN`, `CUSTOMER`
* **[`RoomType.java`](src/main/java/com/stayease/model/RoomType.java):**
  * `SINGLE`, `DOUBLE`, `TRIPLE`, `DORMITORY`
* **[`BookingStatus.java`](src/main/java/com/stayease/model/BookingStatus.java):**
  * `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`
  * Added `public static final Set<BookingStatus> BLOCKING = Collections.unmodifiableSet(EnumSet.of(PENDING, APPROVED));`
  * Added `public boolean isBlocking()` returning `true` only for `PENDING` and `APPROVED`.

### 2.2 Entities (`com.stayease.model`)
* **[`User.java`](src/main/java/com/stayease/model/User.java):**
  * Table: `users`
  * Columns: `id` (GenerationType.IDENTITY), `full_name` (not null, max 100), `email` (not null, unique, max 150), `password_hash` (not null), `phone` (max 20, nullable), `role` (EnumType.STRING, not null), `created_at` (set in `@PrePersist`).
  * Plain getters and setters (no Lombok).
* **[`Room.java`](src/main/java/com/stayease/model/Room.java):**
  * Table: `rooms`
  * Columns: `id` (GenerationType.IDENTITY), `room_number` (not null, unique, max 10), `type` (RoomType, STRING, not null), `capacity` (int, not null), `price_per_night` (BigDecimal precision 10 scale 2, not null), `description` (max 1000), `active` (boolean, default true).
  * Plain getters and setters.
* **[`Booking.java`](src/main/java/com/stayease/model/Booking.java):**
  * Table: `bookings`
  * Associations: `user` (@ManyToOne FetchType.LAZY, not null), `room` (@ManyToOne FetchType.LAZY, not null).
  * Columns: `check_in` (LocalDate, not null), `check_out` (LocalDate, not null), `guests` (int, not null), `status` (BookingStatus, STRING, not null), `admin_remark` (max 500, nullable), `created_at` (set in `@PrePersist`).
  * Composite Index: `idx_bookings_room_dates` on `(room_id, check_in, check_out)`.
  * Plain getters and setters.

### 2.3 Repositories (`com.stayease.repository`)
* **[`UserRepository.java`](src/main/java/com/stayease/repository/UserRepository.java):**
  * `Optional<User> findByEmail(String email);`
  * `boolean existsByEmail(String email);`
* **[`RoomRepository.java`](src/main/java/com/stayease/repository/RoomRepository.java):**
  * Spring Data JPA repository extending `JpaRepository<Room, Long>`.
* **[`BookingRepository.java`](src/main/java/com/stayease/repository/BookingRepository.java):**
  * `List<Booking> findByUserOrderByCreatedAtDesc(User user);`
  * `boolean existsByRoomId(Long roomId);`

### 2.4 Repository Tests (`src/test/java/com/stayease/repository/RepositoryTests.java`)
All `@DataJpaTest` tests verified and passing:
1. Save a User and find by email; `existsByEmail` is true.
2. Saving two users with the same email fails with `DataIntegrityViolationException`.
3. Saving two rooms with the same room number fails with `DataIntegrityViolationException`.
4. Save Room, User, and Booking, then read back with status and dates.
5. `BookingStatus.isBlocking()` is true for `PENDING` and `APPROVED`, false for `REJECTED` and `CANCELLED`.

---

## 3. Permanent UI Design System Specification

Recorded in [`.agents/rules/ui-design-rules.md`](.agents/rules/ui-design-rules.md) and implemented in [`src/main/resources/static/css/app.css`](src/main/resources/static/css/app.css):

### 3.1 Color Palette & Tokens
* **Brand Oxblood:** `#481D2A` (Hover `#35141E`, Pressed `#291019`)
* **Interactive Cobalt:** `#3046D3` (Hover `#2538B5`, Pressed `#1D2C91`, Soft `#EEF0FF`)
* **Accent Tangerine:** `#FF7A30` (Hover `#E9631D`)
* **Warm Cream (Background):** `#FFF8ED`
* **Soft Sand (Secondary):** `#F5EBDD`
* **Charcoal (Text):** `#202020`
* **Muted Text:** `#66615F`
* **Semantic Statuses:**
  * Success / Approved: `#21633E` (Bg `#E5F2E8`, Border `#B8DCC2`)
  * Pending / Warning: `#805400` (Bg `#FFF3D6`, Border `#EAD39A`)
  * Error / Rejected: `#B42318` (Bg `#FDECEC`, Border `#F3B9B5`)
  * Neutral / Cancelled: `#5F6670` (Bg `#EEF0EC`, Border `#D7DCD6`)

### 3.2 Typography & Spacing
* **Headings:** DM Sans (`clamp(2.3rem, 4.5vw, 3.8rem)`)
* **Body:** Inter (16px base, 1.6 line height)
* **Base Spacing:** 4px scale
* **Sticky Footer:** Flexbox layout ensuring footer stays anchored to the viewport bottom.

---

## 4. Phase 3: Shared Layout, Navbar, and Error Handling

### 4.1 Layout Fragments (`src/main/resources/templates/fragments/layout.html`)
* **`head(title)`:** Dynamic document title (`StayEase | {title}`), CDN Bootstrap 5 CSS, Bootstrap Icons, `/css/app.css`.
* **`navbar`:**
  * Brand wordmark linking to `/`.
  * Public link to `/rooms`.
  * Role-based links using `sec:authorize`:
    * Anonymous: `Login` (`/login`), `Register` (`/register`).
    * `ROLE_CUSTOMER`: `Dashboard` (`/customer/dashboard`), `My bookings` (`/customer/bookings`), and `Logout`.
    * `ROLE_ADMIN`: `Admin dashboard` (`/admin/dashboard`), `Manage rooms` (`/admin/rooms`), `Manage bookings` (`/admin/bookings`), and `Logout`.
  * Logout submits via `POST /logout` with CSRF protection token.
  * Collapses into mobile hamburger navigation below 992px (`navbar-expand-lg`).
* **`flash`:** Bootstrap dismissible alerts for `successMessage` and `errorMessage`.
* **`footer`:** Consistent brand footer adhering to the sticky footer structure.
* **`scripts`:** Bootstrap 5 JS bundle.

### 4.2 Error Pages (`src/main/resources/templates/error/`)
* **[`403.html`](src/main/resources/templates/error/403.html):** "Access Denied" — *"You do not have permission to open this page."* with Back to Home button.
* **[`404.html`](src/main/resources/templates/error/404.html):** "Page Not Found" — Friendly page with Back to Home button.
* **[`500.html`](src/main/resources/templates/error/500.html):** "Server Error" — Friendly page with Back to Home button.

### 4.3 Landing Page & Dashboard Views
* **[`index.html`](src/main/resources/templates/index.html):** Uses layout fragments, hero section with "Browse rooms" CTA, room search panel, benefits section, and featured accommodations.
* **[`rooms.html`](src/main/resources/templates/rooms.html):** Catalog with category filters.
* **[`customer/dashboard.html`](src/main/resources/templates/customer/dashboard.html):** Customer portal with greeting, upcoming reservation, and booking history.
* **[`admin/dashboard.html`](src/main/resources/templates/admin/dashboard.html):** Admin workspace with dark oxblood sidebar, metrics, and request approval table.

---

## 5. Verification & Test Suite

### 5.1 Automated Tests (`./mvnw test`)
* **Total Tests:** 11
* **Failures:** 0
* **Errors:** 0
* **Status:** BUILD SUCCESS
  * `StayEaseApplicationTests.contextLoads()`
  * `HomeControllerTest`: `/`, `/rooms`, `/customer/dashboard`, `/admin/dashboard`, and `homePage_anonymousUser_containsBrowseRoomsAndLogin`.
  * `RepositoryTests`: User save/find, duplicate email constraint, duplicate room number constraint, booking save/retrieve, `BookingStatus.isBlocking()`.

### 5.2 Test Coverage (Phases 1 to 13)
* Total Tests: 164 passed (`BUILD SUCCESS`) across Repository, Service, Controller, and Security matrix tests.

---

## 6. Phase 4: User Registration
* Added `PasswordEncoder` BCrypt bean.
* Implemented `RegisterForm` DTO with validation.
* Added `AuthService.register()` with duplicate email protection (`EmailAlreadyUsedException`).
* Created `AuthController` and `register.html` view.

---

## 7. Phase 5: Authentication, Sessions & Route Protection
* Implemented `CustomUserDetailsService` loading user by normalized email with `ROLE_` prefix.
* Added `RoleBasedSuccessHandler` redirecting ADMIN to `/admin/dashboard` and CUSTOMER to `/customer/dashboard`.
* Configured `SecurityConfig` with form login, logout invalidation, cookie deletion, and CSRF protection.
* Added `DataSeeder` for bootstrap admin creation.
* Created `AuthController` `GET /login` and `login.html`.
* Placeholder dashboards for Customer and Admin.

---

## 8. Phase 6: Authorization Matrix & Session Hardening
* Implemented `SecurityAccessTest` parameterized matrix asserting public, customer, and admin routes.
* Verified session fixation protection (`changeSessionId()`), HTTP-only cookies, and CSRF token enforcement.
* Added "Security design" section to `README.md`.

---

## 9. Phase 7: Public Room Browsing, Search & Sample Data
* Added `RoomRepository.searchActiveRooms` JPQL query and `RoomService.searchActiveRooms`.
* Added `RoomNotFoundException` (@ResponseStatus NOT_FOUND).
* Created `RoomController` (`/rooms`, `/rooms/{id}`) and views `rooms/list.html` and `rooms/details.html`.
* Seeded 8 sample rooms in `DataSeeder` (including 1 inactive room).

---

## 10. Phase 8: Admin Room CRUD
* Implemented `RoomForm` DTO with validation.
* Added `DuplicateRoomNumberException`.
* Implemented `RoomService` methods: `findAllForAdmin`, `getForEdit`, `create`, `update`, `delete` (with booking checks).
* Created `AdminRoomController` (`/admin/rooms`, `/admin/rooms/new`, `/admin/rooms/{id}/edit`, `/admin/rooms/{id}/delete`).
* Created `admin/rooms/list.html` and `admin/rooms/form.html`.
* Added comprehensive service, MockMvc, and security matrix tests.

---

## 11. Phase 9: Customer Booking Requests and Booking History
* Implemented `BookingForm` DTO with validation for check-in/check-out dates and guest capacity.
* Created `BookingValidationException`.
* Added `BookingService.createBooking()` and `findBookingsForCustomer()`.
* Implemented `CustomerController` (`GET /customer/bookings`, `GET /customer/book/{roomId}`, `POST /customer/book`).
* Created `customer/booking-form.html` and `customer/bookings.html`.
* Added `CustomerBookingControllerTest` and `BookingServiceTest` coverage.

---

## 12. Phase 10: Overlap Detection, Alternatives and Safe Concurrent Bookings
* Implemented date overlap detection in `BookingRepository.findOverlapping()`.
* Added `BookingConflictException` capturing conflicting date ranges and suggested alternative rooms.
* Concurrency hardening with pessimistic locking query on room reservation (`RoomRepository.findWithPessimisticLockById()`).
* Rendered conflict notices and alternative room recommendations on `customer/booking-form.html`.
* Added 11 dedicated overlap permutation tests in `BookingOverlapTest`.

---

## 13. Phase 11: Admin Approves or Rejects Bookings
* Implemented admin review workflows in `BookingService`: `findAllForAdmin()`, `approveBooking(id)`, `rejectBooking(id, adminRemark)`.
* Handled validation with `BookingNotFoundException` and `InvalidBookingStateException`.
* Created `AdminBookingController` (`GET /admin/bookings`, `POST /admin/bookings/{id}/approve`, `POST /admin/bookings/{id}/reject`).
* Created `admin/bookings.html` management view with tabbed status filtering and rejection modals with remarks.
* Extended `AdminBookingControllerTest` and updated security matrix tests.

---

## 14. Phase 12: Customers Cancel Their Own Bookings
* Implemented customer self-service cancellation in `BookingService.cancelCustomerBooking()`.
* Enforced security ownership validation (customers can only cancel their own bookings) and allowed cancellation only for `PENDING` or `APPROVED` future stays.
* Created `CustomerController` endpoint `POST /customer/bookings/{id}/cancel`.
* Added cancel confirmation modals to `customer/bookings.html` with CSRF protection.
* Added comprehensive tests in `CustomerBookingControllerTest`, `BookingServiceTest`, and `SecurityAccessTest`.

---

## 15. Phase 13: Two Genuinely Different Dashboards
* Implemented `DashboardService` with dedicated data aggregation for each role:
  * **Admin Dashboard:** `AdminDashboardStats` featuring total rooms, active rooms, occupied rooms today, available rooms today, occupancy percentage, pending requests count, 6-month booking trends (`MonthBookingCount`), and urgent pending approvals table.
  * **Customer Dashboard:** `CustomerDashboardView` featuring personal stay stays (upcoming, pending, past stays), hero highlight for the immediate next stay, upcoming itinerary table, and quick room availability search widget (`/customer/availability`).
* Created views: `admin/dashboard.html`, `customer/dashboard.html`, and `customer/availability.html`.
* Added unit, integration, and security tests in `DashboardServiceTest` and `DashboardControllerTest`.

---

## 16. Phase 14: Polish and Responsiveness
* **Spring Data `Pageable` Pagination:**
  * Public rooms catalog (`/rooms`): 9 rooms per page with 1-based page query parameters (`page=1` gives 9 items, `page=3` gives 7 items for 25 rooms total).
  * Admin booking management (`/admin/bookings`): 10 bookings per page.
  * Customer booking management (`/customer/bookings`): 10 bookings per page.
  * Filter query parameters (`type`, `capacity`, `minPrice`, `maxPrice`, `status`) are preserved across pagination links.
  * Out-of-range and negative page numbers do not crash or return HTTP 500; gracefully display the empty state or clamped page.
* **Fragments & Component Reuse:**
  * Created [`templates/fragments/pagination.html`](src/main/resources/templates/fragments/pagination.html) with active filter retention and re-used across rooms, admin bookings, and customer bookings.
  * Created [`templates/fragments/status-badge.html`](src/main/resources/templates/fragments/status-badge.html) providing unified badge styling for all booking statuses (`PENDING` warning, `APPROVED` success, `REJECTED` danger, `CANCELLED` secondary).
* **Responsive Tables & Empty States:**
  * Every data table is wrapped inside `.table-responsive` with `-webkit-overflow-scrolling: touch; max-width: 100%;` preventing parent horizontal overflow.
  * Added friendly, illustrated empty states for rooms catalog, customer bookings, and admin bookings.
* **Form & Interaction Polish:**
  * Explicit `<label>` elements for all input elements and `th:errors` validation display under each field.
  * Date inputs with `min` attributes bound to today's date (`yyyy-MM-dd`).
  * Global double-submit prevention script attached to all forms in `layout.html` scripts fragment.
  * Destructive action confirmation dialogs (`confirm(...)`) on room deletion, customer booking cancellation, and admin rejection.
* **Visual Role Differentiation:**
  * Dark navbar (`#1a1014`) with an orange "Admin" badge next to the brand for `ADMIN` users via `sec:authorize="hasRole('ADMIN')"`.
  * Brand cobalt navbar (`#3046D3`) for `CUSTOMER` and anonymous users via `sec:authorize="!hasRole('ADMIN')"`.
* **Testing:**
  * Added [`PaginationTest.java`](src/test/java/com/stayease/controller/PaginationTest.java) validating room pagination (25 rooms: 9 on page 1, 7 on page 3, empty state on page 99 without 500, filter preservation) and booking pagination.
  * Total suite: 167 tests passing (`BUILD SUCCESS`).

---

## 17. Phase 15: Admin CSV Export of Bookings
* **CSV Export Service:**
  * Implemented [`BookingExportService.java`](src/main/java/com/stayease/service/BookingExportService.java) with `toCsv(List<Booking>)`.
  * Strict header definition: `Booking ID,Customer name,Customer email,Room,Check-in,Checkout,Guests,Status,Total price,Requested on`.
  * Robust RFC-4180 escaping: fields with commas, quotes, or newlines are wrapped in double quotes and inner quotes doubled (`""`).
  * Formula injection mitigation: fields starting with `=`, `+`, `-`, or `@` are prefixed with `'`.
  * Total price formatted with 2 decimal places (`setScale(2, RoundingMode.HALF_UP)`).
* **Controller Endpoint:**
  * Added `GET /admin/bookings/export?status=` to [`AdminBookingController.java`](src/main/java/com/stayease/controller/AdminBookingController.java).
  * Returns `ResponseEntity<byte[]>` with `Content-Type: text/csv; charset=UTF-8` and `Content-Disposition: attachment; filename=bookings-YYYY-MM-DD.csv`.
  * Preserves and respects the active booking status filter (`PENDING`, `APPROVED`, etc.).
* **Template UI:**
  * Added "Export CSV" button in [`admin/bookings.html`](src/main/resources/templates/admin/bookings.html) that dynamically binds `th:href="@{/admin/bookings/export(status=${selectedStatus})}"`.
* **Tests:**
  * Created [`BookingExportServiceTest.java`](src/test/java/com/stayease/service/BookingExportServiceTest.java): validates header row, comma escaping, quote escaping, formula injection neutralization, and formatted data rows.
  * Extended [`AdminBookingControllerTest.java`](src/test/java/com/stayease/controller/AdminBookingControllerTest.java): validates ADMIN 200 with content-type/disposition headers, filter limits, CUSTOMER 403, and anonymous login redirect.
  * Added `/admin/bookings/export` route matrix to [`SecurityAccessTest.java`](src/test/java/com/stayease/security/SecurityAccessTest.java).
  * Total suite: 178 tests passing (`BUILD SUCCESS`).

---

## 18. Git & GitHub Status
* **Branch:** `main`
* **Remote:** [https://github.com/ishangautam140675/StayEase.git](https://github.com/ishangautam140675/StayEase.git)
* **Status:** All tests passing (`BUILD SUCCESS`, 178 tests across all 15 phases).


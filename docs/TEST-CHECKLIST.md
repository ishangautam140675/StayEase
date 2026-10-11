# StayEase — Comprehensive Manual Test Checklist

Use this checklist to perform manual verification of all features, roles, and edge cases. Keep the **Pass/Fail** column handy to record results during testing.

---

## 1. Route Authorization Matrix

Verify every route across all three identity states: **Anonymous**, **Customer (`ROLE_CUSTOMER`)**, and **Admin (`ROLE_ADMIN`)**.

| Route | HTTP Method | Identity | Expected Result | Pass / Fail |
|---|---|---|---|---|
| `/` | GET | Anonymous | 200 OK — Public Landing Page renders with navbar | |
| `/` | GET | Customer | 200 OK — Public Landing Page renders with customer navbar | |
| `/` | GET | Admin | 200 OK — Public Landing Page renders with admin navbar & badge | |
| `/rooms` | GET | Anonymous | 200 OK — Accommodations catalog (9 per page) | |
| `/rooms` | GET | Customer | 200 OK — Accommodations catalog | |
| `/rooms` | GET | Admin | 200 OK — Accommodations catalog | |
| `/rooms/{id}` | GET | Anonymous | 200 OK — Room details page | |
| `/rooms/{id}` | GET | Customer | 200 OK — Room details page with "Book Room" button | |
| `/rooms/{id}` | GET | Admin | 200 OK — Room details page | |
| `/login` | GET | Anonymous | 200 OK — Login page | |
| `/login` | GET | Customer | 200 OK — Login page (or authenticated redirect) | |
| `/login` | GET | Admin | 200 OK — Login page (or authenticated redirect) | |
| `/register` | GET | Anonymous | 200 OK — Registration form | |
| `/register` | GET | Customer | 200 OK — Registration form | |
| `/register` | GET | Admin | 200 OK — Registration form | |
| `/customer/dashboard` | GET | Anonymous | 302 Found — Redirect to `/login` | |
| `/customer/dashboard` | GET | Customer | 200 OK — Customer dashboard rendered | |
| `/customer/dashboard` | GET | Admin | 403 Forbidden | |
| `/customer/bookings` | GET | Anonymous | 302 Found — Redirect to `/login` | |
| `/customer/bookings` | GET | Customer | 200 OK — Customer bookings list (10 per page) | |
| `/customer/bookings` | GET | Admin | 403 Forbidden | |
| `/customer/book/{id}` | GET | Anonymous | 302 Found — Redirect to `/login` | |
| `/customer/book/{id}` | GET | Customer | 200 OK — Booking reservation form | |
| `/customer/book/{id}` | GET | Admin | 403 Forbidden | |
| `/customer/book` | POST | Anonymous | 302 Found — Redirect to `/login` | |
| `/customer/book` | POST | Customer | 302 Redirect to `/customer/bookings` on success | |
| `/customer/book` | POST | Admin | 403 Forbidden | |
| `/customer/bookings/{id}/cancel` | POST | Anonymous | 302 Found — Redirect to `/login` | |
| `/customer/bookings/{id}/cancel` | POST | Customer | 302 Redirect to `/customer/bookings` on success | |
| `/customer/bookings/{id}/cancel` | POST | Admin | 403 Forbidden | |
| `/customer/availability` | GET | Anonymous | 302 Found — Redirect to `/login` | |
| `/customer/availability` | GET | Customer | 200 OK — Availability search results | |
| `/customer/availability` | GET | Admin | 403 Forbidden | |
| `/admin/dashboard` | GET | Anonymous | 302 Found — Redirect to `/login` | |
| `/admin/dashboard` | GET | Customer | 403 Forbidden | |
| `/admin/dashboard` | GET | Admin | 200 OK — Admin dashboard with metrics | |
| `/admin/rooms` | GET | Anonymous | 302 Found — Redirect to `/login` | |
| `/admin/rooms` | GET | Customer | 403 Forbidden | |
| `/admin/rooms` | GET | Admin | 200 OK — Room management table | |
| `/admin/rooms/new` | GET | Anonymous | 302 Found — Redirect to `/login` | |
| `/admin/rooms/new` | GET | Customer | 403 Forbidden | |
| `/admin/rooms/new` | GET | Admin | 200 OK — Add room form | |
| `/admin/rooms/{id}/edit` | GET | Anonymous | 302 Found — Redirect to `/login` | |
| `/admin/rooms/{id}/edit` | GET | Customer | 403 Forbidden | |
| `/admin/rooms/{id}/edit` | GET | Admin | 200 OK — Edit room form | |
| `/admin/rooms/{id}/delete` | POST | Anonymous | 302 Found — Redirect to `/login` | |
| `/admin/rooms/{id}/delete` | POST | Customer | 403 Forbidden | |
| `/admin/rooms/{id}/delete` | POST | Admin | 302 Redirect to `/admin/rooms` | |
| `/admin/bookings` | GET | Anonymous | 302 Found — Redirect to `/login` | |
| `/admin/bookings` | GET | Customer | 403 Forbidden | |
| `/admin/bookings` | GET | Admin | 200 OK — Manage bookings table (10 per page) | |
| `/admin/bookings/{id}/approve` | POST | Anonymous | 302 Found — Redirect to `/login` | |
| `/admin/bookings/{id}/approve` | POST | Customer | 403 Forbidden | |
| `/admin/bookings/{id}/approve` | POST | Admin | 302 Redirect to `/admin/bookings` | |
| `/admin/bookings/{id}/reject` | POST | Anonymous | 302 Found — Redirect to `/login` | |
| `/admin/bookings/{id}/reject` | POST | Customer | 403 Forbidden | |
| `/admin/bookings/{id}/reject` | POST | Admin | 302 Redirect to `/admin/bookings` | |
| `/admin/bookings/export` | GET | Anonymous | 302 Found — Redirect to `/login` | |
| `/admin/bookings/export` | GET | Customer | 403 Forbidden | |
| `/admin/bookings/export` | GET | Admin | 200 OK — Downloads `bookings-YYYY-MM-DD.csv` | |

---

## 2. Form Validation Cases

Verify server-side and client-side error handling for every input form.

### A. Customer Registration Form (`/register`)

| Test Case | Input Values | Expected Feedback | Pass / Fail |
|---|---|---|---|
| Blank Submission | All fields empty | Validation error under every field | |
| Short Full Name | `fullName: "A"` | "Full name must be at least 2 characters" | |
| Invalid Email Format | `email: "not-an-email"` | "Please provide a valid email address" | |
| Duplicate Email | `email: "admin@stayease.com"` | "Email is already registered" | |
| Weak Password | `password: "123"` | "Password must be at least 6 characters" | |
| Password Mismatch | `password: "Secret1", confirmPassword: "Diff"` | "Passwords do not match" | |
| Invalid Phone Format | `phone: "abc1234"` | "Phone must contain 10 to 15 digits" | |
| Valid Submission | Valid details | 302 Redirect to `/login` with success banner | |

### B. Customer Booking Request Form (`/customer/book/{roomId}`)

| Test Case | Input Values | Expected Feedback | Pass / Fail |
|---|---|---|---|
| Past Check-in Date | `checkIn: Yesterday` | Check-in date cannot be in the past | |
| Same-Day Checkout | `checkIn: Tomorrow, checkOut: Tomorrow` | Check-out date must be after check-in date | |
| Checkout Before Check-in | `checkIn: Nov 10, checkOut: Nov 05` | Check-out date must be after check-in date | |
| Zero Guests | `guests: 0` | Guests must be at least 1 | |
| Exceeding Capacity | `guests: 5` on Single Room (cap: 1) | Guests cannot exceed room capacity of 1 | |
| Inactive Room | Inactive room ID in URL | Friendly error that room is not available | |
| Valid Submission | Valid dates & guests | 302 Redirect to `/customer/bookings` with success message | |

### C. Admin Room Form (`/admin/rooms/new` & `edit`)

| Test Case | Input Values | Expected Feedback | Pass / Fail |
|---|---|---|---|
| Blank Room Number | `roomNumber: ""` | "Room number is required" | |
| Duplicate Room Number | `roomNumber: "101"` (existing room) | "Room number already exists" | |
| Zero or Negative Price | `pricePerNight: 0` or `-50` | "Price per night must be greater than 0" | |
| Invalid Capacity | `capacity: 0` | "Capacity must be at least 1" | |
| Valid Submission | Valid details | 302 Redirect to `/admin/rooms` with success banner | |

### D. Admin Booking Rejection Form (`/admin/bookings`)

| Test Case | Input Values | Expected Feedback | Pass / Fail |
|---|---|---|---|
| Empty Remark | Click reject with blank remark input | Rejection remark is required error | |
| Oversized Remark | Remark > 500 characters | Error stating remark cannot exceed 500 characters | |
| Valid Remark | `remark: "Maintenance scheduled"` | Booking changes to REJECTED with remark saved | |

---

## 3. Booking Conflict Permutations & Touching Dates

Test room reservation conflicts using Room 101 with an existing **APPROVED** or **PENDING** booking for **Nov 10 – Nov 15, 2026**.

| Case # | Scenario | New Check-in | New Checkout | Overlap Condition | Expected Result | Pass / Fail |
|---|---|---|---|---|---|---|
| **C1** | Completely Before | Nov 01 | Nov 05 | Neither condition met | **Allowed** (No Conflict) | |
| **C2** | Touching Check-in (Before) | Nov 05 | **Nov 10** | Existing.CheckIn (10) < New.CheckOut (10) is False | **Allowed** (Touching Date) | |
| **C3** | Overlap Head (Earlier to Inside) | Nov 08 | Nov 12 | 10 < 12 (True) AND 15 > 8 (True) | **Rejected** (Conflict Error) | |
| **C4** | Exact Match | **Nov 10** | **Nov 15** | 10 < 15 (True) AND 15 > 10 (True) | **Rejected** (Conflict Error) | |
| **C5** | Enclosed Within | Nov 11 | Nov 14 | 10 < 14 (True) AND 15 > 11 (True) | **Rejected** (Conflict Error) | |
| **C6** | Enclosing Outside | Nov 08 | Nov 18 | 10 < 18 (True) AND 15 > 8 (True) | **Rejected** (Conflict Error) | |
| **C7** | Overlap Tail (Inside to Later) | Nov 13 | Nov 18 | 10 < 18 (True) AND 15 > 13 (True) | **Rejected** (Conflict Error) | |
| **C8** | Touching Checkout (After) | **Nov 15** | Nov 20 | Existing.CheckOut (15) > New.CheckIn (15) is False | **Allowed** (Touching Date) | |
| **C9** | Completely After | Nov 20 | Nov 25 | Neither condition met | **Allowed** (No Conflict) | |
| **C10**| Overlap with CANCELLED booking | Nov 10 | Nov 15 | Cancelled booking is non-blocking | **Allowed** (No Conflict) | |
| **C11**| Overlap with REJECTED booking | Nov 10 | Nov 15 | Rejected booking is non-blocking | **Allowed** (No Conflict) | |

---

## 4. Data Persistence & Restart Verification

Verify state preservation across full application lifecycle restarts.

| Step | Action | Expected Outcome | Pass / Fail |
|---|---|---|---|
| **1** | Register a new customer `persist.user@test.com` and log in. | Account created and logged in. | |
| **2** | Create a room as Admin (`Room 808`, Double, $150). | Room 808 appears in admin and public lists. | |
| **3** | Book Room 808 as customer for Dec 01 – Dec 05. | Booking created in `PENDING` state. | |
| **4** | Stop the application (`Ctrl+C` in shell). | Process terminates cleanly. | |
| **5** | Restart application using `./mvnw spring-boot:run`. | Server initializes without errors. | |
| **6** | Log in as Admin (`admin@stayease.com`). | Customer, Room 808, and pending booking persist intact. | |
| **7** | Approve the pending booking for Room 808. | Status changes to `APPROVED`. | |
| **8** | Export CSV from `/admin/bookings`. | CSV file contains the approved booking record. | |

---

## 5. Responsive Design & Layout Verification

Verify horizontal layout stability and absence of unwanted horizontal scrollbars.

| Page URL | Device Viewport | Test Procedure | Expected Result | Pass / Fail |
|---|---|---|---|---|
| `/` (Landing Page) | 375px (Mobile) | Scroll vertically top to bottom | No horizontal scrollbar; content stacks gracefully | |
| `/` (Landing Page) | 768px (Tablet) | View hero and feature cards | Clean 2-column layout; hamburger menu active | |
| `/` (Landing Page) | 1280px (Desktop) | View full grid and hero | Desktop navbar; 4-column feature grid | |
| `/rooms` (Catalog) | 375px (Mobile) | View filter form & room cards | Full-width cards; filters collapse cleanly | |
| `/rooms` (Catalog) | 768px (Tablet) | View room grid & pagination | 2 cards per row; pagination centered | |
| `/rooms` (Catalog) | 1280px (Desktop) | View 9-room grid & pagination | 3 cards per row; pagination links aligned | |
| `/customer/dashboard`| 375px (Mobile) | View metrics and bookings table | Stat cards stack; table scrollable inside `.table-responsive` | |
| `/customer/dashboard`| 768px (Tablet) | View metrics and quick search | Clean 2x2 grid for metrics; form inline | |
| `/customer/dashboard`| 1280px (Desktop) | View full dashboard | Hero stay card and full itinerary table | |
| `/admin/bookings` | 375px (Mobile) | View table & action buttons | Table scrolls within container; page does not overflow | |
| `/admin/bookings` | 768px (Tablet) | View filter tabs & action buttons | Nav-pills wrap gracefully; actions accessible | |
| `/admin/bookings` | 1280px (Desktop) | View full booking management table | Clean spacious layout with status badges and inline forms | |
| `/admin/dashboard` | 375px / 768px / 1280px | View occupancy card & trend chart | Trend chart bars scale smoothly; no overflow | |

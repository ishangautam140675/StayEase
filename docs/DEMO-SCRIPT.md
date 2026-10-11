# StayEase — 5-Minute Live Demonstration Script

This script walks through a concise, impactful 5-minute live demonstration of StayEase. It demonstrates user registration, role differentiation, room discovery, dynamic price calculation, mathematical conflict detection with alternatives, administrative review and approvals, dashboard intelligence, and security boundaries.

---

## Demo Overview & Timeline (Total: 5 Minutes)

| Segment | Duration | Focus Area |
|---|---|---|
| **Step 1: Customer Onboarding** | 0:00 – 0:45 | Registration, validation, and sign in |
| **Step 2: Room Search & Booking** | 0:45 – 1:45 | Browsing catalog, dynamic pricing, booking request |
| **Step 3: Booking Conflict Demonstration**| 1:45 – 2:30 | Triggering overlap error and viewing alternative suggestions |
| **Step 4: Admin Management & Approvals** | 2:30 – 3:30 | Dark admin theme, reviewing pending requests, approving stay |
| **Step 5: Comparative Dashboards & CSV** | 3:30 – 4:15 | Comparing Customer vs Admin dashboards, downloading CSV |
| **Step 6: Security Boundary Verification**| 4:15 – 5:00 | Showing a customer blocked (HTTP 403) from administrative routes |

---

## Detailed Step-by-Step Script

### Step 1: Customer Registration & Login (0:00 – 0:45)
1. **Open Browser**: Navigate to `http://localhost:8080`.
   - *Speaker note*: *"Notice the Editorial hospitality design system, modern typography, and the cobalt blue brand navbar for public visitors."*
2. **Click "Register"**:
   - Fill in:
     - Full Name: `John Traveler`
     - Email: `john.traveler@example.com`
     - Phone: `9876543210`
     - Password: `Password123`
     - Confirm Password: `Password123`
   - Click **Create Account**.
3. **Login**:
   - Enter `john.traveler@example.com` and `Password123`.
   - Click **Sign In**.
   - You are redirected directly to `/customer/dashboard`.

### Step 2: Search Rooms & Create a Booking (0:45 – 1:45)
1. **Navigate to Accommodations**: Click **Rooms** in the navbar (`/rooms`).
   - *Speaker note*: *"Here we see our Spring Data Pageable catalog displaying 9 rooms per page with filter controls for room type, guest capacity, and price."*
2. **Select Room 101**: Click **View Details** on Room 101, then click **Book This Room**.
3. **Fill Booking Form**:
   - Check-in Date: Select **Nov 10, 2026** (or next month).
   - Check-out Date: Select **Nov 15, 2026** (5 nights).
   - Guests: `1`.
   - *Speaker note*: *"Notice how the total price calculates automatically based on the number of nights ($500.00)."*
4. **Submit Request**: Click **Confirm Booking Request**.
   - You are redirected to `/customer/bookings` with a green success banner and a yellow `PENDING` status badge.

### Step 3: Trigger a Date Conflict & Showcase Alternatives (1:45 – 2:30)
1. **Attempt Conflicting Reservation**:
   - Go back to `/rooms` and click **Book This Room** on Room 101 again.
   - Enter overlapping dates: Check-in **Nov 12, 2026**, Check-out **Nov 17, 2026**.
   - Click **Confirm Booking Request**.
2. **Observe Error & Smart Alternative Suggestions**:
   - *Speaker note*: *"The system immediately blocks the overlapping reservation using our overlap formula. Furthermore, instead of a dead end, StayEase automatically queries available alternative rooms of the same capacity and presents them right on the page."*
3. **Highlight Touching Date Flexibility**:
   - Change check-in date to **Nov 15, 2026** (the exact checkout day of the pending stay).
   - *Speaker note*: *"Touching dates are permitted: guest arrival on the same day the prior guest departs is zero-conflict in hospitality."*

### Step 4: Admin Management & Approvals (2:30 – 3:30)
1. **Switch to Admin Session**:
   - Click **Logout**, then go to `/login`.
   - Sign in as `admin@stayease.com` with password `Admin@1234`.
2. **Observe Role Visual Differentiation**:
   - *Speaker note*: *"Notice how Spring Security seamlessly switches the navbar to a sleek dark oxblood aesthetic with a bright orange 'Admin' badge next to the StayEase brand."*
3. **Review Bookings**:
   - Click **Manage bookings** in the navbar (`/admin/bookings`).
   - Notice the status tabs: `All`, `Pending (1)`, `Approved (0)`, etc.
   - In the table, see John Traveler's pending booking for Room 101.
4. **Approve Booking**:
   - Click the green **Approve** button.
   - The booking instantly transitions to a green `APPROVED` badge with a confirmation flash alert.

### Step 5: Comparative Dashboards & CSV Export (3:30 – 4:15)
1. **Inspect Admin Dashboard**:
   - Click **Admin dashboard** (`/admin/dashboard`).
   - Highlight the live metrics: Total Rooms, Active Rooms, Occupied Rooms Today, and Vacant Rooms Today.
   - Show the 6-Month Booking Trends visual breakdown.
2. **Export Bookings CSV**:
   - Return to `/admin/bookings` and click **Export CSV**.
   - Open or inspect the downloaded `bookings-YYYY-MM-DD.csv`.
   - *Speaker note*: *"The CSV includes full booking details, escaped quotes, and single-quote formula neutralizing prefixes (`=`, `+`, `-`, `@`) preventing spreadsheet formula execution attacks."*

### Step 6: Security Boundary Verification (4:15 – 5:00)
1. **Switch Back to Customer**:
   - Log out from Admin and log back in as `john.traveler@example.com`.
2. **View Customer Dashboard**:
   - In `/customer/dashboard`, John's approved stay is now highlighted in the hero spotlight card with an active countdown.
3. **Attempt Unauthorized Admin Access**:
   - In the browser address bar, manually type: `http://localhost:8080/admin/dashboard` or `/admin/bookings`.
   - Press Enter.
   - *Speaker note*: *"The request is immediately intercepted by Spring Security's authorization filter, returning an explicit HTTP 403 Access Denied error. Role boundaries are enforced at the HTTP filter level, not just UI visibility."*

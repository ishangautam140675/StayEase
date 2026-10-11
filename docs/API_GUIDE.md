# StayEase API Reference & Integration Guide

Complete catalog of REST APIs, webhook listeners, export endpoints, and health probes exposed by StayEase.

---

## 1. Authentication & Security Headers

All interactive requests enforce CSRF tokens (via `X-CSRF-TOKEN` header or form parameter `_csrf`).
Responses include hardened HTTP security headers:
* `Content-Security-Policy`: Restricts scripts, styles, and font origins.
* `X-Frame-Options: DENY`: Mitigates clickjacking.
* `X-Content-Type-Options: nosniff`: Prevents MIME-sniffing exploits.
* `Strict-Transport-Security`: HSTS 1-year preload.
* `Referrer-Policy: strict-origin-when-cross-origin`.

---

## 2. In-App Real-Time Notification Endpoints

### 2.1 Get Notifications & Unread Count
* **Endpoint**: `GET /api/notifications`
* **Security**: Requires authenticated session (`ROLE_CUSTOMER` or `ROLE_ADMIN`)
* **Query Parameters**:
  * `limit` *(optional, integer, default: 15)*: Maximum notifications to return.
* **Sample Response**:
```json
{
  "unreadCount": 2,
  "notifications": [
    {
      "id": 104,
      "recipientEmail": "guest@example.com",
      "targetRole": null,
      "title": "Reservation Approved",
      "message": "Your reservation for Room 101 has been approved.",
      "type": "BOOKING_APPROVED",
      "linkUrl": "/customer/bookings/15/receipt",
      "read": false,
      "createdAt": "2026-10-12T09:30:00"
    }
  ]
}
```

### 2.2 Poll New Notifications (Toast Popups)
* **Endpoint**: `GET /api/notifications/poll`
* **Security**: Requires authenticated session
* **Query Parameters**:
  * `since` *(optional, ISO-8601 timestamp)*: e.g. `2026-10-12T09:29:45`
* **Sample Response**:
```json
{
  "unreadCount": 1,
  "newNotifications": [
    {
      "id": 105,
      "title": "Upcoming Stay Tomorrow!",
      "message": "Reminder: Your stay in Room 101 starts tomorrow.",
      "type": "BOOKING_REMINDER",
      "linkUrl": "/customer/bookings/15/receipt",
      "read": false
    }
  ]
}
```

### 2.3 Mark Single Notification as Read
* **Endpoint**: `POST /api/notifications/{id}/read`
* **Security**: Authenticated user
* **Response**:
```json
{
  "status": "success",
  "unreadCount": 0
}
```

### 2.4 Mark All Notifications as Read
* **Endpoint**: `POST /api/notifications/read-all`
* **Security**: Authenticated user
* **Response**:
```json
{
  "status": "success",
  "unreadCount": 0
}
```

---

## 3. Reporting & CSV Export Endpoints

### 3.1 Financial & Operational Analytics CSV
* **Endpoint**: `GET /admin/reports/export/csv`
* **Security**: Requires `ROLE_ADMIN`
* **Query Parameters**:
  * `startDate` *(optional, ISO date)*: `YYYY-MM-DD`
  * `endDate` *(optional, ISO date)*: `YYYY-MM-DD`
* **Response Headers**:
  * `Content-Type: text/csv;charset=UTF-8`
  * `Content-Disposition: attachment; filename="stayease-analytics-2026-09-01-to-2026-10-12.csv"`
* **Content Includes**:
  * Executive financial summaries: Total Bookings, Active Stays, Total Revenue, Completed Revenue, Occupancy Rate %, Cancellation Rate %.
  * Key Hospitality KPIs: ADR (Average Daily Rate), RevPAR (Revenue Per Available Room), Average Booking Lead Time.
  * Room-by-room utilization grid: Room number, Room type, Nights occupied, Utilization rate %, Total room revenue.

### 3.2 Audit Log History CSV
* **Endpoint**: `GET /admin/operations/audit-logs/export/csv`
* **Security**: Requires `ROLE_ADMIN`
* **Query Parameters**:
  * `adminEmail` *(optional)*: Filter by administrator email
  * `action` *(optional)*: Filter by action enum
  * `start` / `end` *(optional, ISO timestamps)*
* **Response Headers**:
  * `Content-Type: text/csv;charset=UTF-8`
  * `Content-Disposition: attachment; filename="stayease-audit-logs-2026-10-12.csv"`

### 3.3 Bookings Registry CSV
* **Endpoint**: `GET /admin/bookings/export/csv`
* **Security**: Requires `ROLE_ADMIN`
* **Query Parameters**:
  * `status` *(optional)*: `ALL`, `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`
* **Response Headers**:
  * `Content-Type: text/csv;charset=UTF-8`

---

## 4. Payment Gateway & Webhook Endpoints

### 4.1 Payment Session Checkout
* **Endpoint**: `POST /customer/bookings/{id}/pay`
* **Security**: Requires `ROLE_CUSTOMER`
* **Response**: Redirects to gateway checkout or confirms instant sandbox payment.

### 4.2 Razorpay Webhook Callback
* **Endpoint**: `POST /api/webhooks/razorpay`
* **Security**: CSRF-exempt; cryptographically authenticated via `X-Razorpay-Signature` HMAC SHA-256 header.
* **Payload**: Standard Razorpay Webhook Event JSON (`payment.captured`, `payment.failed`, `refund.processed`).
* **Response**:
```json
{
  "status": "processed",
  "eventId": "evt_sample12345"
}
```

---

## 5. Actuator Monitoring & Health Probes

### 5.1 Public Liveness Probe
* **Endpoint**: `GET /actuator/health`
* **Access**: Public / Unauthenticated
* **Response**:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

### 5.2 Application Metadata Probe
* **Endpoint**: `GET /actuator/info`
* **Access**: Public / Unauthenticated
* **Response**:
```json
{
  "app": {
    "name": "StayEase",
    "version": "0.0.1-SNAPSHOT"
  }
}
```

# StayEase — Architectural Viva & Defense Reference Notes

Plain-language explanations for critical architectural, security, concurrency, and mathematical concepts implemented in StayEase.

---

### 1. Why BCrypt for Password Hashing?
* **Slow by Design (Work Factor)**: Traditional hashing algorithms like MD5 or SHA-256 were designed for speed, making them vulnerable to brute-force and GPU hardware attacks (billions of hashes/sec). BCrypt is an adaptive hash function based on the Blowfish cipher that incorporates a configurable cost factor (work factor).
* **Automatic Per-Password Salt**: BCrypt automatically generates a cryptographic 16-byte random salt and prepends it to the resulting hash string. This renders rainbow table lookup attacks completely ineffective.
* **Timing-Attack Resistance**: Spring Security's `BCryptPasswordEncoder.matches()` validates passwords using constant-time string comparisons, preventing attackers from measuring CPU cycle response times to infer password characters.

---

### 2. How Are User Roles Enforced?
* **Spring Security Authority Hierarchy**: Roles are mapped to `GrantedAuthority` objects (`ROLE_CUSTOMER` and `ROLE_ADMIN`).
* **HTTP Request Filter Security**: Enforced in `SecurityConfig` via `authorizeHttpRequests`:
  - `.requestMatchers("/admin/**").hasRole("ADMIN")`
  - `.requestMatchers("/customer/**").hasRole("CUSTOMER")`
  - `.requestMatchers("/", "/rooms/**", "/login", "/register", ...).permitAll()`
  - `.anyRequest().authenticated()`
* **Server-Side Enforcement**: Even if a customer inspects or types an administrative URL like `/admin/rooms`, the `AuthorizationFilter` intercepts the request before controller invocation and returns HTTP 403 Forbidden.
* **UI Adaptability (`sec:authorize`)**: The Thymeleaf Spring Security dialect shows or hides elements (e.g., dark admin navbar with Admin badge vs blue customer navbar) based on the session's authenticated principal authorities.

---

### 3. Why Is CSRF Protection Enabled?
* **Cross-Site Request Forgery Defense**: Without CSRF protection, an authenticated user visiting a malicious external website could be tricked into executing unwanted HTTP requests against StayEase (e.g. an invisible `<form>` posting to `/customer/bookings/{id}/cancel` using the user's ambient session cookie).
* **Synchronizer Token Pattern**: StayEase enforces CSRF protection across all state-changing requests (`POST`). A unique, unpredictable, cryptographically random token is generated per session. Every form submits this token via a hidden `_csrf` input field. Spring Security validates the incoming token against the session before delegating to controller endpoints.

---

### 4. Why Use DTOs and Jakarta Validation?
* **Separation of Concerns**: DTOs (Data Transfer Objects like `RegisterForm`, `RoomForm`, `BookingForm`) decouple external HTTP request payloads from internal JPA persistence entities (`User`, `Room`, `Booking`).
* **Over-Posting / Mass Assignment Protection**: Prevents malicious clients from sending unexpected fields (such as injecting `role=ADMIN` or setting `createdAt` directly on entity attributes).
* **Declarative Validation**: Jakarta Bean Validation annotations (`@NotBlank`, `@Min`, `@NotNull`, `@Size`) catch invalid, malformed, or malicious input at the presentation boundary before business services or database layers are reached.
* **BindingResult UX**: Allows field-level errors (`th:errors`) to be cleanly rendered directly below the affected inputs without losing user data or causing database crashes.

---

### 5. What Do the Architectural Layers Do?
StayEase follows strict Separation of Concerns using a layered architecture:
* **Controller Layer (`com.stayease.controller`)**: Handles HTTP requests, routes, session authentication resolution, input binding, form validation checks, and view template resolution. Contains zero business logic.
* **Service Layer (`com.stayease.service`)**: Encapsulates all domain business rules, orchestration, transaction management (`@Transactional`), overlap validation, price calculation, and email/CSV generation.
* **Repository Layer (`com.stayease.repository`)**: Spring Data JPA interfaces encapsulating database queries, JPQL projections, custom counts, and pessimistic locking queries.
* **Entity / Model Layer (`com.stayease.model`)**: JPA entities mapping tables, foreign keys, relationships (`@ManyToOne`), lifecycle callbacks (`@PrePersist`), and domain constraints.

---

### 6. How Does the Booking Overlap Formula Work and Why Are Touching Dates Allowed?
* **Mathematical Formula**:
  $$\text{Existing.CheckIn} < \text{New.CheckOut} \quad \text{AND} \quad \text{Existing.CheckOut} > \text{New.CheckIn}$$
* **De Morgan's Theorem Equivalence**:
  The non-overlapping condition is:
  $$\text{New.CheckOut} \le \text{Existing.CheckIn} \quad \text{OR} \quad \text{New.CheckIn} \ge \text{Existing.CheckOut}$$
  Negating the non-overlapping condition yields the canonical overlap formula.
* **Why Touching Dates Are Allowed**:
  If Guest A departs on Nov 5 (`Existing.CheckOut = 5`) and Guest B arrives on Nov 5 (`New.CheckIn = 5`):
  - In the overlap formula, `Existing.CheckOut > New.CheckIn` becomes `5 > 5`, which is **false**.
  - Since both conditions must be true for a conflict, the formula evaluates to **false** (no overlap).
  - This perfectly models hospitality check-out (11:00 AM) and check-in (2:00 PM) on the same date, maximizing room occupancy.

---

### 7. Why Is the Room Row Locked in a Transaction (`SELECT ... FOR UPDATE`)?
* **Race Condition Hazard**: In a concurrent environment without locking, two customers could simultaneously attempt to book Room 101 for the same dates.
  - Request 1 checks for overlapping bookings: none found.
  - Request 2 checks for overlapping bookings: none found.
  - Request 1 inserts booking and commits.
  - Request 2 inserts booking and commits.
  - Result: **Double-booking disaster**.
* **Pessimistic Write Lock Solution**:
  - `RoomRepository.findByIdForUpdate(roomId)` executes `SELECT ... FROM rooms WHERE id = ? FOR UPDATE`.
  - The database acquires an exclusive row-level lock on Room 101.
  - Request 2 is forced to wait until Request 1's transaction either commits or rolls back.
  - When Request 2 acquires the lock, its subsequent overlap query immediately discovers Request 1's newly committed booking and throws `BookingConflictException`.

---

### 8. How Do Sessions and Authentication Work?
* **Spring Security Context & Filter Chain**: When a user submits credentials to `/login`, `DaoAuthenticationProvider` retrieves user details via `CustomUserDetailsService`, verifies the BCrypt hash, and creates an `Authentication` token stored in the `SecurityContext`.
* **Stateful HTTP Session**: The `SecurityContext` is stored in the user's `HttpSession` under key `SPRING_SECURITY_CONTEXT`.
* **Session ID & Cookie**: The client receives a `JSESSIONID` cookie configured with:
  - `HttpOnly = true` (inaccessible to JavaScript, defending against XSS cookie theft).
  - `timeout = 30m` (expires after 30 minutes of inactivity).
* **Session Fixation Protection**: Spring Security calls `changeSessionId()` upon successful login, preventing session fixation attacks where an attacker pre-sets a known session ID.

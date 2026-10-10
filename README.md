# StayEase

A Hostel and PG Booking System built with Java 21, Spring Boot 3, Thymeleaf, and MySQL.

## Environment Variables

The application reads the following environment variables at startup.
**Never hardcode these values** — set them in your shell or a `.env` file that is excluded from version control.

| Variable      | Description                           | Example value                                                              |
|---------------|---------------------------------------|----------------------------------------------------------------------------|
| `DB_URL`      | JDBC URL for the MySQL database       | `jdbc:mysql://localhost:3306/stayease?createDatabaseIfNotExist=true&serverTimezone=UTC` |
| `DB_USERNAME` | MySQL username                        | `root`                                                                     |
| `DB_PASSWORD` | MySQL password (**required, no default**) | `yourpassword`                                                         |
| `ADMIN_EMAIL` | Bootstrap admin email (optional seed) | `admin@stayease.com`                                                       |
| `ADMIN_PASSWORD` | Bootstrap admin password (optional seed) | `Admin@1234`                                                            |

## Running Locally

```bash
# 1. Set environment variables (PowerShell example)
$env:DB_URL      = "jdbc:mysql://localhost:3306/stayease?createDatabaseIfNotExist=true&serverTimezone=UTC"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "yourpassword"

# 2. Start the application
./mvnw spring-boot:run

# 3. Open in browser
# http://localhost:8080
```

## Running Tests

Tests use H2 in-memory database — no MySQL required.

```bash
./mvnw test
```

## Tech Stack

| Layer        | Technology                          |
|--------------|-------------------------------------|
| Language     | Java 21                             |
| Framework    | Spring Boot 3.x                     |
| Web / Views  | Spring MVC + Thymeleaf + Bootstrap 5 |
| Persistence  | Spring Data JPA / Hibernate / MySQL 8 |
| Security     | Spring Security (form login, BCrypt)|
| Validation   | Jakarta Bean Validation             |
| Tests        | JUnit 5, MockMvc, H2                |

## Security Design

- **Password Hashing (BCrypt)**: User and administrator passwords are encrypted using Spring Security's `BCryptPasswordEncoder` with salted hashes. Plaintext passwords are never stored in the database or written to log files.
- **Role-Based Access Control**: Two distinct roles are enforced via Spring Security authorities:
  - `CUSTOMER`: Access to the customer booking portal (`/customer/**`).
  - `ADMIN`: Access to administrative management portals (`/admin/**`).
- **URL Authorization Rules**:
  - **Public**: `/`, `/rooms`, `/rooms/**`, `/register`, `/login`, static assets (`/css/**`, `/js/**`, `/images/**`), and error pages.
  - **Role-Guarded**: `/customer/**` requires `ROLE_CUSTOMER`; `/admin/**` requires `ROLE_ADMIN`. Cross-role access returns `403 Forbidden`.
  - **Authenticated**: Any other endpoint requires an authenticated session; unauthenticated requests redirect to `/login`.
- **CSRF Protection**: Spring Security Cross-Site Request Forgery (CSRF) protection is enabled by default. All state-changing POST requests (e.g., `/register`, `/login`, `/logout`) require a valid CSRF token.
- **Session Hardening**:
  - **HTTP-Only Cookies**: `server.servlet.session.cookie.http-only=true` prevents client-side script access to the `JSESSIONID` cookie (mitigating XSS session theft).
  - **Session Fixation**: Configured with `changeSessionId()` so session identifiers are renewed upon successful authentication.
  - **Logout Cleanup**: Form logout (`POST /logout`) invalidates the HTTP session, cleans up the security context, deletes the `JSESSIONID` cookie, and redirects to `/login?logout`.

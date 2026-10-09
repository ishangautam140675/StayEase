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

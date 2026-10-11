# ==============================================================================
# StayEase Multi-Stage Production Dockerfile
# Base: Eclipse Temurin JDK 21 (Builder) & JRE 21 (Runtime)
# Non-root user, optimized JVM cgroup ergonomics, and healthcheck.
# ==============================================================================

# ── Stage 1: Build Application Artifact ───────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

# Copy Maven wrapper and POM
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Copy application source code and package
COPY src ./src
RUN ./mvnw clean package -DskipTests=true -B

# ── Stage 2: Production Minimal Runtime ──────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

# Metadata labels
LABEL maintainer="StayEase Operations <ops@stayease.com>"
LABEL app="stayease"
LABEL version="1.0.0"

# Install curl for container health check
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Create non-root system group and user
RUN groupadd -g 10001 stayease && \
    useradd -u 10001 -g stayease -s /bin/bash -m stayease

WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder --chown=stayease:stayease /workspace/target/stayease-0.0.1-SNAPSHOT.jar /app/stayease.jar

# Create log directory with correct permissions
RUN mkdir -p /app/logs && chown -R stayease:stayease /app/logs

# Switch to non-root user
USER stayease:stayease

# Expose internal application port
EXPOSE 8080

# Environment defaults
ENV SPRING_PROFILES_ACTIVE=prod
ENV PORT=8080
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Container Healthcheck via Spring Boot Actuator
HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Execute Spring Boot Application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/stayease.jar"]

# Runtime-only Dockerfile for Acadia API Gateway
# Expects pre-built JAR from CI pipeline

# INF-1: pinned base image tag (replace with a digest in CI for full immutability).
FROM eclipse-temurin:21.0.5_11-jre AS runner
WORKDIR /app

# INF-1: install curl for the container health check, then run as a non-root user.
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -r appgroup && useradd -r -g appgroup appuser

# Copy pre-built JAR (CI already produces ./build/libs/*.jar)
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar
RUN chown -R appuser:appgroup /app

# INF-1: drop root privileges.
USER appuser

# Expose default port (configurable via server.port)
EXPOSE 8080

# Environment configuration
ARG APP_ENV=prod
ENV APP_ENV=${APP_ENV}

# Timezone setting
ENV TZ=Asia/Seoul

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# INF-1: container health check against the actuator endpoint.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Run Spring Boot application
# - Uses APP_ENV for profile selection
# - Servlet (Tomcat) + virtual threads
ENTRYPOINT ["sh", "-c", "java \
    $JAVA_OPTS \
    -Dspring.profiles.active=${APP_ENV} \
    -Djava.security.egd=file:/dev/./urandom \
    -jar app.jar"]

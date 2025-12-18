# Runtime-only Dockerfile for Acadia API Gateway
# Expects pre-built JAR from CI pipeline

FROM eclipse-temurin:21-jre AS runner
WORKDIR /app

# Copy pre-built JAR (CI already produces ./build/libs/*.jar)
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# Expose default port (configurable via server.port)
EXPOSE 8080

# Environment configuration
ARG APP_ENV=prod
ENV APP_ENV=${APP_ENV}

# Timezone setting
ENV TZ=Asia/Seoul

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run Spring Boot application
# - Uses APP_ENV for profile selection
# - Netty-based WebFlux server (no Tomcat)
ENTRYPOINT ["sh", "-c", "java \
    $JAVA_OPTS \
    -Dspring.profiles.active=${APP_ENV} \
    -Djava.security.egd=file:/dev/./urandom \
    -jar app.jar"]

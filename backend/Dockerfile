# Stage 1: Build
FROM gradle:8.12-jdk21-alpine AS build
WORKDIR /app
# Copy Gradle wrapper and configuration files first for better layer caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
# Initial build to download dependencies only
RUN ./gradlew build -x test --no-daemon > /dev/null 2>&1 || true

# Copy source code and perform full build
COPY src src
RUN ./gradlew build -x test --no-daemon

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Copy the built JAR file from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Optimized launch command for 512MB RAM (Render)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Xss256k", "-jar", "app.jar"]

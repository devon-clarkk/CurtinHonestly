# Stage 1: Build
FROM gradle:8.12-jdk21-alpine AS build
WORKDIR /app

# Copy Gradle wrapper and configuration files first
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Ensure gradlew has execution permissions and correct line endings for Linux
RUN chmod +x gradlew && \
    sed -i 's/\r$//' gradlew

# Pre-download dependencies to cache this layer
RUN ./gradlew dependencies --no-daemon

# Copy source code and perform full build
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the bootable JAR
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

# Optimized launch command for 512MB RAM (Render)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Xss256k", "-jar", "app.jar"]
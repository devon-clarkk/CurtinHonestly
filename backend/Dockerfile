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

# Install curl and python (needed for seeder script)
RUN apk add --no-cache curl python3 py3-requests

# Copy only the bootable JAR
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

# # Copy the seeder scripts and data
# COPY populate_db.sh generate_reviews.py ./
# COPY src/test/java/com/curtinhonestly/backend/httprequest/populate_units.http /app/src/test/java/com/curtinhonestly/backend/httprequest/populate_units.http
# RUN sed -i 's/\r$//' populate_db.sh && \
#     sed -i 's/\r$//' generate_reviews.py && \
#     sed -i 's/\r$//' /app/src/test/java/com/curtinhonestly/backend/httprequest/populate_units.http && \
#     chmod +x populate_db.sh generate_reviews.py

# Optimized launch command for 512MB RAM (Render)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Xss256k", "-jar", "app.jar"]
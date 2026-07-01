package com.curtinhonestly.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        // Azure Container Apps secret names must be lowercase-hyphen; local/docker may use SCREAMING_SNAKE.
        applyEnv("spring.datasource.url", "database-url", "DATABASE_URL");
        applyEnv("spring.datasource.username", "database-username", "DATABASE_USERNAME");
        applyEnv("spring.datasource.password", "database-password", "DATABASE_PASSWORD");
        applyEnv("jwt.secret", "jwt-secret", "JWT_SECRET");

        SpringApplication.run(Application.class, args);
    }

    private static void applyEnv(String property, String azureKey, String legacyKey) {
        String value = firstNonNull(System.getenv(azureKey), System.getenv(legacyKey));
        if (value != null) {
            System.setProperty(property, value);
        }
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }
}

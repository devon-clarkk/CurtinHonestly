package com.curtinhonestly.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl != null) {
            System.setProperty("spring.datasource.url", dbUrl);
        }

        String dbUsername = System.getenv("DATABASE_USERNAME");
        if (dbUsername != null) {
            System.setProperty("spring.datasource.username", dbUsername);
        }

        String dbPassword = System.getenv("DATABASE_PASSWORD");
        if (dbPassword != null) {
            System.setProperty("spring.datasource.password", dbPassword);
        }

        SpringApplication.run(Application.class, args);
    }
}

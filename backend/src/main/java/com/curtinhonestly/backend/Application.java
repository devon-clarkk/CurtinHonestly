package com.curtinhonestly.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.net.URI;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		String dbUrl = System.getenv("DATABASE_URL");
		if (dbUrl != null && (dbUrl.startsWith("postgresql://") || dbUrl.startsWith("postgres://"))) {
			String cleanUrl = dbUrl.startsWith("postgres://") ?
					"postgresql://" + dbUrl.substring("postgres://".length()) : dbUrl;
			System.setProperty("DATABASE_URL", "jdbc:" + cleanUrl);

			try {
				URI uri = new URI(cleanUrl);
				String userInfo = uri.getUserInfo();
				if (userInfo != null && userInfo.contains(":")) {
					String[] parts = userInfo.split(":", 2);
					System.setProperty("DATABASE_USERNAME", parts[0]);
					System.setProperty("DATABASE_PASSWORD", parts[1]);
				}
			} catch (Exception e) {
				// Ignore parsing errors, fall back to defaults or environment
			}
		}
		SpringApplication.run(Application.class, args);
	}

}

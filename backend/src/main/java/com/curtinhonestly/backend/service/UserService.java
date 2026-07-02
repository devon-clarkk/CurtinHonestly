package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackOn = Exception.class)
public class UserService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    public User createUser(String email, String password) {
        log.info("Creating user: {}", email);
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setUsername(email); // Set username as email for simplicity

        // Assign USER role by default
        user.setRoles(Arrays.asList(UserRole.ROLE_USER));

        User savedUser = userRepo.saveAndFlush(user);
        log.info("User created successfully with ID: {}", savedUser.getId());
        return savedUser;
    }

    // Method to create admin user
    public User createAdminUser(String email, String password) {
        log.info("Creating admin user: {}", email);
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setUsername(email);

        // Assign ADMIN and USER roles
        user.setRoles(Arrays.asList(UserRole.ROLE_ADMIN, UserRole.ROLE_USER));

        User savedUser = userRepo.saveAndFlush(user);
        log.info("Admin user created successfully with ID: {}", savedUser.getId());
        return savedUser;
    }

    public User getUserByEmail(String email) {
        return userRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }
}

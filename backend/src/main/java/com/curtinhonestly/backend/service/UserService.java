package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Campaign;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.util.EmailNormalizer;
import com.curtinhonestly.backend.util.StudentEmailValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackOn = Exception.class)
public class UserService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    public User createUser(String email, String password) {
        return createUser(email, password, null, null);
    }

    public User createUser(String email, String password, Campaign campaign, String ref) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        log.info("Creating user: {}", normalizedEmail);

        if (userRepo.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("That email is already registered.");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        // Verified status is now earned by confirming an emailed link (VerificationService),
        // never granted by the email suffix alone. New accounts start unverified.
        user.setVerifiedStudent(false);
        user.setRoles(List.of(UserRole.ROLE_USER));

        if (campaign != null) {
            user.setCampaign(campaign);
            user.setRegisteredViaRef(ref != null && !ref.isBlank() ? ref.trim() : campaign.getSlug());
        }

        User savedUser = userRepo.saveAndFlush(user);
        log.info("User created successfully with ID: {}, verifiedStudent={}, campaign={}",
                savedUser.getId(), savedUser.isVerifiedStudent(),
                campaign != null ? campaign.getSlug() : "none");
        return savedUser;
    }

    public User createAdminUser(String email, String password) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        log.info("Creating admin user: {}", normalizedEmail);
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setVerifiedStudent(StudentEmailValidator.isStudentEmail(normalizedEmail));
        user.setRoles(List.of(UserRole.ROLE_ADMIN, UserRole.ROLE_USER));

        User savedUser = userRepo.saveAndFlush(user);
        log.info("Admin user created successfully with ID: {}", savedUser.getId());
        return savedUser;
    }

    public User getUserByEmail(String email) {
        return userRepo.findByEmail(EmailNormalizer.normalize(email)).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User updateEmail(String currentEmail, String newEmail, String password) {
        User user = getUserByEmail(currentEmail);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid password.");
        }

        if (newEmail == null || !newEmail.contains("@") || !newEmail.contains(".")) {
            throw new IllegalArgumentException("Please provide a valid email address.");
        }

        String normalizedEmail = EmailNormalizer.normalize(newEmail);
        userRepo.findByEmail(normalizedEmail)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("That email is already in use.");
                });

        user.setEmail(normalizedEmail);
        // Changing the login email drops verified status — it must be re-earned by
        // confirming a link sent to the new address (VerificationService).
        user.setVerifiedStudent(false);
        User savedUser = userRepo.saveAndFlush(user);
        log.info("User {} updated email", savedUser.getId());
        return savedUser;
    }

    public void deleteAccount(String email, String password) {
        User user = getUserByEmail(email);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid password.");
        }

        userRepo.delete(user);
        log.info("User {} deleted their account", user.getId());
    }
}

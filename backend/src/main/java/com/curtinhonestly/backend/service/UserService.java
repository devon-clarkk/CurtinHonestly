package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Campaign;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.util.EmailNormalizer;
import com.curtinhonestly.backend.util.StudentEmailValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackOn = Exception.class)
public class UserService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final ReviewRepo reviewRepo;
    private final UnitAggregateService unitAggregateService;
    private final EmailService emailService;

    public User createUser(String email, String password) {
        return createUser(email, password, null, null);
    }

    public User createUser(String email, String password, Collection<Campaign> campaigns, String ref) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        log.info("Creating user: {}", normalizedEmail);

        var existing = userRepo.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            // Do the bcrypt work anyway before throwing. The caller (AuthController)
            // returns an identical response for "created" and "already exists" so the
            // endpoint can't be used to enumerate accounts (security audit finding #7),
            // but a uniform body is defeated by a stopwatch if the create path spends
            // ~100ms hashing and this path returns instantly. The result is discarded.
            passwordEncoder.encode(password);
            // Tell the actual owner instead of the caller. This is the standard
            // companion to an enumeration-safe signup: the person who really owns the
            // address learns about the attempt, and the person making it learns nothing.
            notifyExistingAccount(normalizedEmail);
            throw new EmailAlreadyRegisteredException("That email is already registered.");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        // Verified status is now earned by confirming an emailed link (VerificationService),
        // never granted by the email suffix alone. New accounts start unverified.
        user.setVerifiedStudent(false);
        user.setRoles(List.of(UserRole.ROLE_USER));

        Set<Campaign> enrolments = campaigns == null ? Set.of() : new HashSet<>(campaigns);
        if (!enrolments.isEmpty()) {
            user.setCampaigns(enrolments);
        }
        // Record the referral slug even when there's no campaign enrolment, so
        // tracking-only referral links (no campaigns, ref set) still attribute the
        // signup. Falls back to a joined campaign's slug when a reward signup omits ref.
        String normalizedRef = ref != null && !ref.isBlank()
                ? ref.trim()
                : enrolments.stream().findFirst().map(Campaign::getSlug).orElse(null);
        if (normalizedRef != null) {
            user.setRegisteredViaRef(normalizedRef);
        }

        User savedUser = userRepo.saveAndFlush(user);
        log.info("User created successfully with ID: {}, verifiedStudent={}, campaigns={}",
                savedUser.getId(), savedUser.isVerifiedStudent(), enrolments.size());
        return savedUser;
    }

    // Best-effort: EmailService swallows its own failures, and this runs on a path
    // that is about to roll back, so it must never be the reason a request errors.
    private void notifyExistingAccount(String normalizedEmail) {
        emailService.send(
                normalizedEmail,
                "Someone tried to sign up with your CurtinHonestly email",
                """
                        Someone just tried to create a CurtinHonestly account using this email
                        address, but you already have one.

                        If that was you, sign in instead, or use "Forgot password" if you can't
                        remember your password. Your account and password have not changed.

                        If it wasn't you, you can ignore this email. Nobody can see that this
                        address has an account, and no changes were made to it.
                        """);
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
        // Same cut-off as a password reset: an email change is a credential change,
        // so sessions issued before it are revoked (security audit finding #4).
        // Truncated to whole seconds on purpose, because the caller mints a replacement
        // token immediately, and a JWT's `iat` is whole seconds, so a nanosecond
        // stamp would make that fresh token look stale and log the user straight out.
        user.setTokensValidAfter(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        User savedUser = userRepo.saveAndFlush(user);
        log.info("User {} updated email", savedUser.getId());
        return savedUser;
    }

    private static final int MAX_COMPLETED_UNITS = 200;

    public Set<String> getCompletedUnitCodes(String email) {
        return getUserByEmail(email).getCompletedUnitCodes();
    }

    // Replaces the whole set rather than merging, so removing a unit is as
    // simple as omitting it from the next call — matches how the frontend
    // manages the list as a single editable chip collection.
    public Set<String> updateCompletedUnitCodes(String email, Set<String> rawCodes) {
        User user = getUserByEmail(email);

        Set<String> normalized = (rawCodes == null ? Set.<String>of() : rawCodes).stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase())
                .collect(Collectors.toCollection(HashSet::new));

        if (normalized.size() > MAX_COMPLETED_UNITS) {
            throw new IllegalArgumentException("You can record at most " + MAX_COMPLETED_UNITS + " completed units.");
        }

        user.setCompletedUnitCodes(normalized);
        userRepo.saveAndFlush(user);
        return normalized;
    }

    /**
     * Delete the account. By default (deleteReviews = false) reviews are
     * anonymized — detached from the account, not deleted — because the review
     * content is the site's asset, not the identity behind it. Passing
     * deleteReviews = true is an explicit secondary option that also removes
     * every review the user wrote and recalculates the affected units' aggregates.
     */
    public void deleteAccount(String email, String password, boolean deleteReviews) {
        User user = getUserByEmail(email);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid password.");
        }

        List<Review> reviews = reviewRepo.findByUser_IdOrderByCreatedAtDesc(user.getId());

        if (deleteReviews) {
            Set<String> affectedUnitIds = reviews.stream()
                    .map(r -> r.getUnit().getId())
                    .collect(Collectors.toSet());
            reviewRepo.deleteAll(reviews);
            reviewRepo.flush();
            affectedUnitIds.forEach(unitAggregateService::recalculateForUnit);
        } else {
            // Ratings/content are unchanged, only authorship is severed — no
            // aggregate recalculation needed.
            reviews.forEach(review -> review.setUser(null));
            reviewRepo.saveAll(reviews);
        }

        userRepo.delete(user);
        log.info("User {} deleted their account ({} reviews {})",
                user.getId(), reviews.size(), deleteReviews ? "removed" : "anonymized");
    }
}

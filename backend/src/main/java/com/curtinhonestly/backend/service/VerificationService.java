package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.VerificationPurpose;
import com.curtinhonestly.backend.domain.VerificationToken;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.repo.VerificationTokenRepo;
import com.curtinhonestly.backend.util.EmailNormalizer;
import com.curtinhonestly.backend.util.StudentEmailValidator;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues and confirms hashed, single-use email-verification tokens. Ownership of
 * the emailed link is what actually flips {@code verifiedStudent} — the suffix
 * check alone no longer grants the badge.
 */
@Service
@Slf4j
@Transactional(rollbackOn = Exception.class)
public class VerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final VerificationTokenRepo tokenRepo;
    private final UserRepo userRepo;
    private final EmailService emailService;
    private final String frontendBaseUrl;
    private final Duration ttl;

    public VerificationService(VerificationTokenRepo tokenRepo,
                               UserRepo userRepo,
                               EmailService emailService,
                               @Value("${app.frontend-base-url:http://localhost:4200}") String frontendBaseUrl,
                               @Value("${app.mail.verification-token-ttl-hours:24}") long ttlHours) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * Create a fresh student-verification token for {@code targetStudentEmail} and
     * email a confirmation link to it. Does not change the account until confirmed.
     */
    public void requestStudentVerification(User user, String targetStudentEmail) {
        if (user.isVerifiedStudent()) {
            throw new IllegalStateException("Account is already verified as a student.");
        }
        String normalized = EmailNormalizer.normalize(targetStudentEmail);
        if (!StudentEmailValidator.isStudentEmail(normalized)) {
            throw new IllegalArgumentException("A valid @student.curtin.edu.au email is required.");
        }
        assertEmailNotTakenByOther(normalized, user);

        String rawToken = newRawToken();
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setPurpose(VerificationPurpose.STUDENT_VERIFICATION);
        token.setTargetEmail(normalized);
        token.setExpiresAt(Instant.now().plus(ttl));

        tokenRepo.invalidateOutstanding(user, VerificationPurpose.STUDENT_VERIFICATION);
        tokenRepo.save(token);

        emailService.send(normalized, "Verify your Curtin student email", buildEmailBody(rawToken));
        log.info("Issued student-verification token for user {} -> {}", user.getId(), normalized);
    }

    /**
     * Confirm a token from an emailed link: mark the account verified (and switch
     * its login email to the verified student address), then consume the token.
     */
    public User confirmStudentVerification(String rawToken) {
        VerificationToken token = tokenRepo.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("This verification link is invalid."));

        if (token.getPurpose() != VerificationPurpose.STUDENT_VERIFICATION) {
            throw new IllegalArgumentException("This verification link is invalid.");
        }
        if (!token.isUsable(Instant.now())) {
            throw new IllegalArgumentException("This verification link has expired or has already been used.");
        }

        User user = token.getUser();
        String studentEmail = token.getTargetEmail();
        // Re-check ownership at confirm time — the address may have been claimed since the link was issued.
        assertEmailNotTakenByOther(studentEmail, user);

        user.setEmail(studentEmail);
        user.setVerifiedStudent(true);
        userRepo.saveAndFlush(user);

        token.setUsedAt(Instant.now());
        tokenRepo.save(token);

        log.info("User {} confirmed student verification for {}", user.getId(), studentEmail);
        return user;
    }

    private void assertEmailNotTakenByOther(String normalizedEmail, User user) {
        userRepo.findByEmail(normalizedEmail)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("That student email is already linked to another account.");
                });
    }

    private String buildEmailBody(String rawToken) {
        String link = frontendBaseUrl + "/verify-student/confirm?token=" + rawToken;
        return """
                Confirm your Curtin student email to earn the "Verified Curtin Student" badge on CurtinHonestly.

                Click the link below (valid for %d hours):
                %s

                If you didn't request this, you can safely ignore this email.
                """.formatted(ttl.toHours(), link);
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * Issues and confirms hashed, single-use email-verification tokens. Ownership of
 * the emailed link is what actually flips {@code verifiedStudent}: the suffix
 * check alone does not grant the badge.
 */
@Service
@Slf4j
@Transactional(rollbackOn = Exception.class)
public class VerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MIN_PASSWORD_LENGTH = 8;

    static final String STUDENT_LINK_INVALID = "This verification link is invalid.";
    static final String STUDENT_LINK_EXPIRED =
            "This verification link has expired. Request a new one from your account page.";
    static final String STUDENT_LINK_USED =
            "This verification link has already been used. Request a new one from your account page.";

    private final VerificationTokenRepo tokenRepo;
    private final UserRepo userRepo;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final String frontendBaseUrl;
    private final Duration ttl;
    private final Duration resetTtl;

    public VerificationService(VerificationTokenRepo tokenRepo,
                               UserRepo userRepo,
                               EmailService emailService,
                               PasswordEncoder passwordEncoder,
                               @Value("${app.frontend-base-url:http://localhost:4200}") String frontendBaseUrl,
                               @Value("${app.mail.verification-token-ttl-hours:24}") long ttlHours,
                               @Value("${app.mail.reset-token-ttl-hours:1}") long resetTtlHours) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.ttl = Duration.ofHours(ttlHours);
        this.resetTtl = Duration.ofHours(resetTtlHours);
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

        String link = frontendBaseUrl + "/verify-student/confirm?token=" + rawToken;
        emailService.send(normalized, "Verify your Curtin student email",
                buildEmailBody(link), buildEmailHtml(link));
        log.info("Issued student-verification token for user {} -> {}", user.getId(), normalized);
    }

    /**
     * Confirm a token from an emailed link: mark the account verified (and switch
     * its login email to the verified student address), then consume the token.
     *
     * <p>Replaying a spent link is a success, not an error, when the account already
     * holds exactly what that link would have granted (verified, and logged in as the
     * link's target email) and the link is still inside its original expiry window.
     * Student mailboxes sit behind link scanners that open the emailed URL before the
     * person does, and a second click from the person themselves must not be told the
     * link is dead when the verification it asked for has in fact happened. The expiry
     * bound is what keeps this from turning every old verification email into a
     * standing login link: after {@code expiresAt} a used token is dead for good.
     *
     * <p>Every rejection is logged at INFO with the branch it hit, so a "could not
     * verify" report can be diagnosed from the logs without the raw token or the
     * address (the user id identifies the account well enough).
     */
    public User confirmStudentVerification(String rawToken) {
        String tokenHash = hash(rawToken);
        VerificationToken token = tokenRepo.findByTokenHash(tokenHash).orElse(null);
        if (token == null) {
            log.info("Student-verification confirm rejected: no token matches hash prefix {}",
                    tokenHash.substring(0, 12));
            throw new IllegalArgumentException(STUDENT_LINK_INVALID);
        }

        User user = token.getUser();
        if (token.getPurpose() != VerificationPurpose.STUDENT_VERIFICATION) {
            log.info("Student-verification confirm rejected for user {}: token purpose is {}",
                    user.getId(), token.getPurpose());
            throw new IllegalArgumentException(STUDENT_LINK_INVALID);
        }

        Instant now = Instant.now();
        String studentEmail = token.getTargetEmail();
        boolean expired = !token.getExpiresAt().isAfter(now);

        if (token.getUsedAt() != null) {
            boolean alreadyGranted = user.isVerifiedStudent()
                    && studentEmail != null
                    && studentEmail.equalsIgnoreCase(user.getEmail());
            if (alreadyGranted && !expired) {
                log.info("Student-verification confirm for user {}: token already used at {} but the account "
                        + "already holds that verification; treating the replay as success", user.getId(),
                        token.getUsedAt());
                return user;
            }
            log.info("Student-verification confirm rejected for user {}: token already used at {} "
                    + "(account verified={}, expired={})", user.getId(), token.getUsedAt(),
                    user.isVerifiedStudent(), expired);
            throw new IllegalArgumentException(expired ? STUDENT_LINK_EXPIRED : STUDENT_LINK_USED);
        }
        if (expired) {
            log.info("Student-verification confirm rejected for user {}: token expired at {}",
                    user.getId(), token.getExpiresAt());
            throw new IllegalArgumentException(STUDENT_LINK_EXPIRED);
        }

        // Re-check ownership at confirm time: the address may have been claimed since the link was issued.
        assertEmailNotTakenByOther(studentEmail, user);

        user.setEmail(studentEmail);
        user.setVerifiedStudent(true);
        userRepo.saveAndFlush(user);

        token.setUsedAt(now);
        tokenRepo.save(token);

        log.info("User {} confirmed student verification for {}", user.getId(), studentEmail);
        return user;
    }

    /**
     * Issue a password-reset token and email a link to the account address.
     * Enumeration-safe: if no account exists for the email, this silently does
     * nothing (the caller returns an identical response either way).
     */
    public void requestPasswordReset(String email) {
        String normalized = EmailNormalizer.normalize(email);
        var user = userRepo.findByEmail(normalized).orElse(null);
        if (user == null) {
            log.info("Password reset requested for unknown email (no-op)");
            return;
        }

        String rawToken = newRawToken();
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setPurpose(VerificationPurpose.PASSWORD_RESET);
        token.setTargetEmail(normalized);
        token.setExpiresAt(Instant.now().plus(resetTtl));

        tokenRepo.invalidateOutstanding(user, VerificationPurpose.PASSWORD_RESET);
        tokenRepo.save(token);

        String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
        emailService.send(normalized, "Reset your CurtinHonestly password",
                buildPasswordResetBody(link), buildPasswordResetHtml(link));
        log.info("Issued password-reset token for user {}", user.getId());
    }

    /**
     * Complete a password reset from an emailed link: set the new password and
     * consume the token.
     */
    public void resetPassword(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }

        VerificationToken token = tokenRepo.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("This reset link is invalid."));

        if (token.getPurpose() != VerificationPurpose.PASSWORD_RESET) {
            throw new IllegalArgumentException("This reset link is invalid.");
        }
        if (!token.isUsable(Instant.now())) {
            throw new IllegalArgumentException("This reset link has expired or has already been used.");
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        // Cut every session that predates the reset. A reset is what someone does
        // after a device theft or a token leak, so leaving previously issued JWTs
        // alive for the rest of their 7-day TTL made the reset false assurance
        // (security audit finding #4). Truncated to whole seconds to match the JWT
        // `iat` granularity the filter compares against.
        user.setTokensValidAfter(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        userRepo.saveAndFlush(user);

        token.setUsedAt(Instant.now());
        tokenRepo.save(token);

        log.info("User {} completed a password reset; sessions issued before now are invalidated", user.getId());
    }

    private void assertEmailNotTakenByOther(String normalizedEmail, User user) {
        userRepo.findByEmail(normalizedEmail)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("That student email is already linked to another account.");
                });
    }

    private String buildEmailBody(String link) {
        return """
                Confirm your Curtin student email to earn the "Verified Curtin Student" badge on CurtinHonestly.

                Open the link below and press "Confirm my student email" (valid for %d hours):
                %s

                If you didn't request this, you can safely ignore this email.
                """.formatted(ttl.toHours(), link);
    }

    private String buildEmailHtml(String link) {
        return htmlEmail(
                "Confirm your Curtin student email to earn the <strong>Verified Curtin Student</strong> "
                        + "badge on CurtinHonestly.",
                "Confirm my student email",
                link,
                "The link is valid for " + ttl.toHours() + " hours. It opens a page with one button; "
                        + "pressing that button completes the verification.",
                "If you didn't request this, you can safely ignore this email.");
    }

    private String buildPasswordResetBody(String link) {
        return """
                We received a request to reset your CurtinHonestly password.

                Open the link below to choose a new password (valid for %d hour(s)):
                %s

                If you didn't request this, you can safely ignore this email. Your
                password won't change until you use the link.
                """.formatted(resetTtl.toHours(), link);
    }

    private String buildPasswordResetHtml(String link) {
        return htmlEmail(
                "We received a request to reset your CurtinHonestly password.",
                "Choose a new password",
                link,
                "The link is valid for " + resetTtl.toHours() + " hour(s).",
                "If you didn't request this, you can safely ignore this email. "
                        + "Your password won't change until you use the link.");
    }

    /**
     * Minimal inline-styled HTML so the link is a real anchor. A bare URL in a
     * plain-text body relies on each mail client guessing where the URL ends, and a
     * guess that stops one character short produces exactly one symptom: a link that
     * looks right and cannot be validated. The visible URL under the button gives a
     * copy-and-paste fallback for clients that block buttons or strip styling.
     */
    private static String htmlEmail(String intro, String buttonLabel, String link, String validity, String footer) {
        return """
                <!doctype html>
                <html lang="en"><body style="font-family: Arial, Helvetica, sans-serif; color: #1f2933; line-height: 1.5;">
                <p>%s</p>
                <p style="margin: 24px 0;">
                  <a href="%s" style="display: inline-block; padding: 12px 20px; background: #0a2540; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold;">%s</a>
                </p>
                <p>%s</p>
                <p>If the button does not work, copy this link into your browser:<br>
                <a href="%s" style="color: #0a2540; word-break: break-all;">%s</a></p>
                <p style="color: #616e7c; font-size: 13px;">%s</p>
                </body></html>
                """.formatted(intro, link, buttonLabel, validity, link, link, footer);
    }

    /**
     * 256 bits of randomness rendered as lowercase hex. Hex keeps the token to a
     * single character class, so no mail client's URL detection can treat a trailing
     * {@code -} or {@code _} as punctuation and cut the link one character short.
     * Confirmation only ever looks the hash up, so links issued under an earlier
     * encoding keep working until they expire.
     */
    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
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

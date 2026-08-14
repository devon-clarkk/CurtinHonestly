package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.VerificationPurpose;
import com.curtinhonestly.backend.domain.VerificationToken;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.repo.VerificationTokenRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock VerificationTokenRepo tokenRepo;
    @Mock UserRepo userRepo;
    @Mock EmailService emailService;
    @Mock PasswordEncoder passwordEncoder;

    @Captor ArgumentCaptor<VerificationToken> tokenCaptor;
    @Captor ArgumentCaptor<String> bodyCaptor;

    private VerificationService service() {
        return new VerificationService(tokenRepo, userRepo, emailService, passwordEncoder,
                "https://curtinhonestly.com/", 24, 1);
    }

    private User unverifiedUser() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("alice@gmail.com");
        user.setVerifiedStudent(false);
        return user;
    }

    private static String sha256(String raw) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    // ---- requestStudentVerification ----

    @Test
    void request_issuesHashedTokenAndEmailsLinkMatchingTheStoredHash() throws Exception {
        when(userRepo.findByEmail("bob@student.curtin.edu.au")).thenReturn(Optional.empty());
        VerificationService service = service();

        service.requestStudentVerification(unverifiedUser(), "  Bob@Student.Curtin.Edu.Au ");

        verify(tokenRepo).invalidateOutstanding(any(User.class), eq(VerificationPurpose.STUDENT_VERIFICATION));
        verify(tokenRepo).save(tokenCaptor.capture());
        verify(emailService).send(eq("bob@student.curtin.edu.au"), anyString(), bodyCaptor.capture());

        VerificationToken saved = tokenCaptor.getValue();
        assertThat(saved.getPurpose()).isEqualTo(VerificationPurpose.STUDENT_VERIFICATION);
        assertThat(saved.getTargetEmail()).isEqualTo("bob@student.curtin.edu.au");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(23, ChronoUnit.HOURS));

        // The raw token in the emailed link must hash to the stored hash — the raw value is never persisted.
        Matcher m = Pattern.compile("token=([^\\s]+)").matcher(bodyCaptor.getValue());
        assertThat(m.find()).isTrue();
        String rawToken = m.group(1);
        assertThat(saved.getTokenHash()).isEqualTo(sha256(rawToken));
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
    }

    @Test
    void request_rejectsAlreadyVerifiedAccount() {
        User user = unverifiedUser();
        user.setVerifiedStudent(true);

        assertThatThrownBy(() -> service().requestStudentVerification(user, "bob@student.curtin.edu.au"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(emailService);
    }

    @Test
    void request_rejectsNonStudentEmail() {
        assertThatThrownBy(() -> service().requestStudentVerification(unverifiedUser(), "bob@gmail.com"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokenRepo, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void request_rejectsEmailTakenByAnotherAccount() {
        User other = new User();
        other.setId("user-2");
        when(userRepo.findByEmail("bob@student.curtin.edu.au")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service().requestStudentVerification(unverifiedUser(), "bob@student.curtin.edu.au"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokenRepo, never()).save(any());
    }

    // ---- confirmStudentVerification ----

    private VerificationToken usableToken(User user) {
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setPurpose(VerificationPurpose.STUDENT_VERIFICATION);
        token.setTargetEmail("bob@student.curtin.edu.au");
        token.setTokenHash("hash");
        token.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        return token;
    }

    @Test
    void confirm_verifiesAccountAndConsumesToken() throws Exception {
        User user = unverifiedUser();
        VerificationToken token = usableToken(user);
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));
        when(userRepo.findByEmail("bob@student.curtin.edu.au")).thenReturn(Optional.empty());

        User result = service().confirmStudentVerification("raw-token");

        assertThat(result.isVerifiedStudent()).isTrue();
        assertThat(result.getEmail()).isEqualTo("bob@student.curtin.edu.au");
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepo).saveAndFlush(user);
        verify(tokenRepo).save(token);
    }

    @Test
    void confirm_rejectsUnknownToken() {
        when(tokenRepo.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().confirmStudentVerification("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirm_rejectsExpiredToken() throws Exception {
        VerificationToken token = usableToken(unverifiedUser());
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().confirmStudentVerification("raw-token"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void confirm_rejectsAlreadyUsedToken() throws Exception {
        VerificationToken token = usableToken(unverifiedUser());
        token.setUsedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().confirmStudentVerification("raw-token"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void confirm_rejectsTokenIssuedForAnotherPurpose() throws Exception {
        VerificationToken token = usableToken(unverifiedUser());
        token.setPurpose(VerificationPurpose.PASSWORD_RESET);
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().confirmStudentVerification("raw-token"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void confirm_rejectsWhenTargetEmailClaimedSinceIssue() throws Exception {
        User user = unverifiedUser();
        VerificationToken token = usableToken(user);
        User other = new User();
        other.setId("user-2");
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));
        when(userRepo.findByEmail("bob@student.curtin.edu.au")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service().confirmStudentVerification("raw-token"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepo, never()).saveAndFlush(any());
    }

    // ---- requestPasswordReset ----

    @Test
    void requestReset_issuesResetTokenAndEmailsLinkMatchingStoredHash() throws Exception {
        User user = unverifiedUser();
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(Optional.of(user));

        service().requestPasswordReset("  Alice@Gmail.com ");

        verify(tokenRepo).invalidateOutstanding(user, VerificationPurpose.PASSWORD_RESET);
        verify(tokenRepo).save(tokenCaptor.capture());
        verify(emailService).send(eq("alice@gmail.com"), anyString(), bodyCaptor.capture());

        VerificationToken saved = tokenCaptor.getValue();
        assertThat(saved.getPurpose()).isEqualTo(VerificationPurpose.PASSWORD_RESET);
        // reset TTL is 1h in the test config
        assertThat(saved.getExpiresAt()).isBefore(Instant.now().plus(2, ChronoUnit.HOURS));

        Matcher m = Pattern.compile("token=([^\\s]+)").matcher(bodyCaptor.getValue());
        assertThat(m.find()).isTrue();
        assertThat(saved.getTokenHash()).isEqualTo(sha256(m.group(1)));
        assertThat(bodyCaptor.getValue()).contains("/reset-password?token=");
    }

    @Test
    void requestReset_isSilentForUnknownEmail() {
        when(userRepo.findByEmail("nobody@gmail.com")).thenReturn(Optional.empty());

        service().requestPasswordReset("nobody@gmail.com");

        verify(tokenRepo, never()).save(any());
        verifyNoInteractions(emailService);
    }

    // ---- resetPassword ----

    private VerificationToken usableResetToken(User user) {
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setPurpose(VerificationPurpose.PASSWORD_RESET);
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        return token;
    }

    @Test
    void resetPassword_setsEncodedPasswordAndConsumesToken() throws Exception {
        User user = unverifiedUser();
        user.setPassword("old-hash");
        VerificationToken token = usableResetToken(user);
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");

        service().resetPassword("raw-token", "new-password-123");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepo).saveAndFlush(user);
        verify(tokenRepo).save(token);
    }

    @Test
    void resetPassword_stampsTheCutOffThatInvalidatesAlreadyIssuedSessions() throws Exception {
        User user = unverifiedUser();
        user.setPassword("old-hash");
        VerificationToken token = usableResetToken(user);
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");

        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        service().resetPassword("raw-token", "new-password-123");

        // Security audit finding #4: JWTs are stateless with a 7-day TTL, so without
        // this stamp a reset did not end the session it was meant to end: a stolen
        // token stayed usable for the rest of the week. JwtAuthenticationFilter refuses
        // any token issued before this instant.
        assertThat(user.getTokensValidAfter()).isNotNull();
        assertThat(user.getTokensValidAfter()).isBetween(before, Instant.now());
        // Whole seconds, matching the JWT `iat` granularity the filter compares against.
        assertThat(user.getTokensValidAfter().getNano()).isZero();
    }

    @Test
    void resetPassword_leavesTheCutOffAloneWhenTheTokenIsRejected() throws Exception {
        User user = unverifiedUser();
        VerificationToken token = usableResetToken(user);
        token.setUsedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().resetPassword("raw-token", "new-password-123"))
                .isInstanceOf(IllegalArgumentException.class);

        // A failed reset must not log anyone out: replaying a spent link should not be
        // usable as a way to kill an account's live sessions.
        assertThat(user.getTokensValidAfter()).isNull();
    }

    @Test
    void resetPassword_rejectsTooShortPasswordBeforeTouchingToken() {
        assertThatThrownBy(() -> service().resetPassword("raw-token", "short"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokenRepo, never()).findByTokenHash(anyString());
    }

    @Test
    void resetPassword_rejectsUnknownToken() {
        when(tokenRepo.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().resetPassword("nope", "new-password-123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetPassword_rejectsExpiredToken() throws Exception {
        VerificationToken token = usableResetToken(unverifiedUser());
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().resetPassword("raw-token", "new-password-123"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void resetPassword_rejectsUsedToken() throws Exception {
        VerificationToken token = usableResetToken(unverifiedUser());
        token.setUsedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().resetPassword("raw-token", "new-password-123"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void resetPassword_rejectsTokenOfWrongPurpose() throws Exception {
        VerificationToken token = usableResetToken(unverifiedUser());
        token.setPurpose(VerificationPurpose.STUDENT_VERIFICATION);
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().resetPassword("raw-token", "new-password-123"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepo, never()).saveAndFlush(any());
    }
}

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
    @Captor ArgumentCaptor<String> htmlCaptor;

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
        verify(emailService).send(eq("bob@student.curtin.edu.au"), anyString(), bodyCaptor.capture(), htmlCaptor.capture());

        VerificationToken saved = tokenCaptor.getValue();
        assertThat(saved.getPurpose()).isEqualTo(VerificationPurpose.STUDENT_VERIFICATION);
        assertThat(saved.getTargetEmail()).isEqualTo("bob@student.curtin.edu.au");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(23, ChronoUnit.HOURS));

        // The raw token in the emailed link must hash to the stored hash: the raw value is never persisted.
        Matcher m = Pattern.compile("token=([^\\s]+)").matcher(bodyCaptor.getValue());
        assertThat(m.find()).isTrue();
        String rawToken = m.group(1);
        assertThat(saved.getTokenHash()).isEqualTo(sha256(rawToken));
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);

        // Lowercase hex only. A token ending in "-" or "_" is one a mail client's URL
        // detection can trim as trailing punctuation, which leaves a link that looks
        // right and cannot be validated.
        assertThat(rawToken).matches("[0-9a-f]{64}");
        // The HTML part carries the same link as a real anchor, plus the copyable URL.
        String link = "https://curtinhonestly.com/verify-student/confirm?token=" + rawToken;
        assertThat(htmlCaptor.getValue()).contains("href=\"" + link + "\"");
        assertThat(htmlCaptor.getValue()).contains("copy this link");
        assertThat(bodyCaptor.getValue()).contains(link);
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
    void confirm_rejectsAlreadyUsedTokenWhenTheAccountIsStillUnverified() throws Exception {
        // Used, but the account did not end up verified (the token was superseded by a
        // re-request, or the account was verified and later changed its email). There
        // is nothing to be idempotent about, so it stays an error, and one that says
        // "used" rather than "expired" so the person knows to request a fresh link.
        VerificationToken token = usableToken(unverifiedUser());
        token.setUsedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().confirmStudentVerification("raw-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(VerificationService.STUDENT_LINK_USED);
        verify(userRepo, never()).saveAndFlush(any());
    }

    private User verifiedUser(String email) {
        User user = unverifiedUser();
        user.setEmail(email);
        user.setVerifiedStudent(true);
        return user;
    }

    @Test
    void confirm_treatsAReplayOfAUsedTokenAsSuccessWhenTheAccountAlreadyHoldsThatVerification() throws Exception {
        // The scenario behind "this link has already been used" reports: a mailbox link
        // scanner opened the emailed URL first. Whoever consumed the token, the account
        // now holds exactly what this link grants, so the person's own click succeeds.
        User user = verifiedUser("bob@student.curtin.edu.au");
        VerificationToken token = usableToken(user);
        token.setUsedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        User result = service().confirmStudentVerification("raw-token");

        assertThat(result).isSameAs(user);
        assertThat(result.isVerifiedStudent()).isTrue();
        // Nothing is rewritten on a replay: the token keeps its original usedAt and the
        // ownership re-check is not needed for an address the account already holds.
        verify(userRepo, never()).saveAndFlush(any());
        verify(tokenRepo, never()).save(any());
        verify(userRepo, never()).findByEmail(anyString());
    }

    @Test
    void confirm_replayMatchesTheTargetEmailCaseInsensitively() throws Exception {
        User user = verifiedUser("Bob@Student.Curtin.Edu.Au");
        VerificationToken token = usableToken(user);
        token.setUsedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThat(service().confirmStudentVerification("raw-token")).isSameAs(user);
    }

    @Test
    void confirm_rejectsAReplayForAVerifiedAccountWhoseEmailIsNotTheTokensTarget() throws Exception {
        // Verified under some other student address: this link did not grant the
        // current state, so replaying it must not mint a session off it.
        User user = verifiedUser("someone-else@student.curtin.edu.au");
        VerificationToken token = usableToken(user);
        token.setUsedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().confirmStudentVerification("raw-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(VerificationService.STUDENT_LINK_USED);
    }

    @Test
    void confirm_rejectsAReplayOnceTheTokenHasExpiredEvenForAVerifiedAccount() throws Exception {
        // The idempotent replay is bounded by the original expiry. Without this bound a
        // spent verification email would be a standing login link for the account.
        User user = verifiedUser("bob@student.curtin.edu.au");
        VerificationToken token = usableToken(user);
        token.setUsedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().confirmStudentVerification("raw-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(VerificationService.STUDENT_LINK_EXPIRED);
    }

    @Test
    void confirm_expiredAndNeverUsedSaysExpiredNotUsed() throws Exception {
        VerificationToken token = usableToken(unverifiedUser());
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(tokenRepo.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().confirmStudentVerification("raw-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(VerificationService.STUDENT_LINK_EXPIRED);
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
        verify(emailService).send(eq("alice@gmail.com"), anyString(), bodyCaptor.capture(), htmlCaptor.capture());

        VerificationToken saved = tokenCaptor.getValue();
        assertThat(saved.getPurpose()).isEqualTo(VerificationPurpose.PASSWORD_RESET);
        // reset TTL is 1h in the test config
        assertThat(saved.getExpiresAt()).isBefore(Instant.now().plus(2, ChronoUnit.HOURS));

        Matcher m = Pattern.compile("token=([^\\s]+)").matcher(bodyCaptor.getValue());
        assertThat(m.find()).isTrue();
        assertThat(saved.getTokenHash()).isEqualTo(sha256(m.group(1)));
        assertThat(bodyCaptor.getValue()).contains("/reset-password?token=");
        assertThat(htmlCaptor.getValue()).contains("href=\"https://curtinhonestly.com/reset-password?token=" + m.group(1) + "\"");
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

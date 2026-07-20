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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Captor ArgumentCaptor<VerificationToken> tokenCaptor;
    @Captor ArgumentCaptor<String> bodyCaptor;

    private VerificationService service() {
        return new VerificationService(tokenRepo, userRepo, emailService, "https://curtinhonestly.com/", 24);
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
}

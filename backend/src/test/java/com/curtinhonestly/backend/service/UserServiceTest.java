package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepo userRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ReviewRepo reviewRepo;
    @Mock UnitAggregateService unitAggregateService;
    @Mock EmailService emailService;

    @Captor ArgumentCaptor<List<Review>> reviewsCaptor;

    private UserService service() {
        return new UserService(userRepo, passwordEncoder, reviewRepo, unitAggregateService, emailService);
    }

    private User user() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("alice@gmail.com");
        user.setPassword("hashed");
        return user;
    }

    private Review reviewFor(User user, String unitId) {
        Unit unit = new Unit();
        unit.setId(unitId);
        Review review = new Review();
        review.setId("review-" + unitId);
        review.setUser(user);
        review.setUnit(unit);
        return review;
    }

    @Test
    void deleteAccount_rejectsWrongPassword() {
        User user = user();
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service().deleteAccount("alice@gmail.com", "wrong", false))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepo, never()).delete(any());
    }

    @Test
    void deleteAccount_defaultAnonymizesReviewsInsteadOfDeletingThem() {
        User user = user();
        Review review = reviewFor(user, "unit-1");
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(reviewRepo.findByUser_IdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(review));

        service().deleteAccount("alice@gmail.com", "correct", false);

        assertThat(review.getUser()).isNull();
        verify(reviewRepo).saveAll(reviewsCaptor.capture());
        assertThat(reviewsCaptor.getValue()).containsExactly(review);
        verify(reviewRepo, never()).deleteAll(anyList());
        verifyNoInteractions(unitAggregateService);
        verify(userRepo).delete(user);
    }

    @Test
    void deleteAccount_withDeleteReviewsTrueRemovesReviewsAndRecalculatesAffectedUnits() {
        User user = user();
        Review reviewA = reviewFor(user, "unit-1");
        Review reviewB = reviewFor(user, "unit-2");
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(reviewRepo.findByUser_IdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(reviewA, reviewB));

        service().deleteAccount("alice@gmail.com", "correct", true);

        verify(reviewRepo).deleteAll(List.of(reviewA, reviewB));
        verify(reviewRepo, never()).saveAll(anyList());
        verify(unitAggregateService).recalculateForUnit("unit-1");
        verify(unitAggregateService).recalculateForUnit("unit-2");
        verify(userRepo).delete(user);
    }

    @Test
    void deleteAccount_withNoReviewsRecalculatesNothing() {
        User user = user();
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(reviewRepo.findByUser_IdOrderByCreatedAtDesc("user-1")).thenReturn(List.of());

        service().deleteAccount("alice@gmail.com", "correct", true);

        // deleteAll(emptyList) is a harmless no-op; what matters is no unit gets recalculated.
        verifyNoInteractions(unitAggregateService);
        verify(userRepo).delete(user);
    }

    @Test
    void updateCompletedUnitCodes_normalizesTrimsAndUppercases() {
        User user = user();
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(java.util.Optional.of(user));

        Set<String> result = service().updateCompletedUnitCodes(
                "alice@gmail.com", Set.of(" comp1000 ", "ISYS2001", ""));

        assertThat(result).containsExactlyInAnyOrder("COMP1000", "ISYS2001");
        assertThat(user.getCompletedUnitCodes()).containsExactlyInAnyOrder("COMP1000", "ISYS2001");
        verify(userRepo).saveAndFlush(user);
    }

    @Test
    void updateCompletedUnitCodes_rejectsOverTwoHundredEntries() {
        User user = user();
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(java.util.Optional.of(user));
        Set<String> tooMany = new java.util.HashSet<>();
        for (int i = 0; i < 201; i++) {
            tooMany.add("UNIT" + i);
        }

        assertThatThrownBy(() -> service().updateCompletedUnitCodes("alice@gmail.com", tooMany))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepo, never()).saveAndFlush(any());
    }

    // ---- registration enumeration (security audit finding #7) ----

    @Test
    void createUser_onDuplicateEmailThrowsTheDistinctTypeSoTheControllerCanAnswerUniformly() {
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(java.util.Optional.of(user()));

        assertThatThrownBy(() -> service().createUser("Alice@Gmail.com", "password123"))
                // The distinct subtype is what lets AuthController.register recognise
                // exactly this case and return the same 200 it returns for a new signup,
                // without also swallowing unrelated IllegalArgumentExceptions (a bad
                // promo code, an invalid campaign) that must still surface as errors.
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void createUser_onDuplicateEmailStillHashesThePasswordSoTimingDoesNotLeak() {
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(java.util.Optional.of(user()));

        assertThatThrownBy(() -> service().createUser("alice@gmail.com", "password123"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        // A uniform response body is worthless if the duplicate branch returns in 1ms
        // while the create branch spends ~100ms in bcrypt: the fork is then readable
        // with a stopwatch. The hash is computed and discarded purely for that parity.
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void createUser_onDuplicateEmailNotifiesTheRealOwnerAndNotTheCaller() {
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(java.util.Optional.of(user()));

        assertThatThrownBy(() -> service().createUser("Alice@Gmail.com", "password123"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        // The person who owns the address is told; the person who made the request is
        // told nothing. Sent to the normalized address, so case games don't dodge it.
        verify(emailService).send(eq("alice@gmail.com"), anyString(), anyString());
    }

    @Test
    void createUser_forANewEmailSendsNoSuchNotice() {
        when(userRepo.findByEmail("newbie@gmail.com")).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepo.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = service().createUser("Newbie@Gmail.com", "password123");

        assertThat(created.getEmail()).isEqualTo("newbie@gmail.com");
        verifyNoInteractions(emailService);
    }

    // ---- session invalidation (security audit finding #4) ----

    @Test
    void updateEmail_stampsTheCredentialChangeCutOffTruncatedToWholeSeconds() {
        User user = user();
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(java.util.Optional.of(user));
        when(userRepo.findByEmail("alice.new@gmail.com")).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(userRepo.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        java.time.Instant before = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        service().updateEmail("alice@gmail.com", "Alice.New@Gmail.com", "correct");

        assertThat(user.getTokensValidAfter()).isNotNull();
        assertThat(user.getTokensValidAfter()).isBetween(before, java.time.Instant.now());
        // Truncation is load-bearing, not tidiness. AuthController mints a replacement
        // token immediately after this call and a JWT's `iat` is whole seconds, so a
        // nanosecond-precision stamp would be strictly newer than that fresh token and
        // the filter would reject it, logging the user out the instant they changed
        // their email. See AppUserDetails.isTokenStale.
        assertThat(user.getTokensValidAfter().getNano()).isZero();
    }

    @Test
    void updateEmail_withAWrongPasswordChangesNothingIncludingTheCutOff() {
        User user = user();
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service().updateEmail("alice@gmail.com", "new@gmail.com", "wrong"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.getTokensValidAfter()).isNull();
        assertThat(user.getEmail()).isEqualTo("alice@gmail.com");
    }

    @Test
    void getCompletedUnitCodes_returnsUsersSet() {
        User user = user();
        user.setCompletedUnitCodes(Set.of("COMP1000"));
        when(userRepo.findByEmail("alice@gmail.com")).thenReturn(java.util.Optional.of(user));

        assertThat(service().getCompletedUnitCodes("alice@gmail.com")).containsExactly("COMP1000");
    }
}

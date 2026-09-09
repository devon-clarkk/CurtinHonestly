package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.AdminNotificationEvent;
import com.curtinhonestly.backend.domain.AdminNotificationSettings;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitRequest;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.AdminNotificationEventDTO;
import com.curtinhonestly.backend.dto.AdminNotificationSettingsDTO;
import com.curtinhonestly.backend.repo.AdminNotificationSettingsRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The alerts hang off the student-facing write paths, so the two things that
 * matter most are that a switched-off or misconfigured alert is silent, and that
 * nothing here can ever fail the student's request.
 */
@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {

    private static final String DEFAULT_RECIPIENT = "owner@example.test";

    @Mock AdminNotificationSettingsRepo settingsRepo;
    @Mock EmailService emailService;

    @Captor ArgumentCaptor<String> subjectCaptor;
    @Captor ArgumentCaptor<String> textCaptor;
    @Captor ArgumentCaptor<String> htmlCaptor;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // The executor runs inline so a queued send is observable in the same test.
    private AdminNotificationService service(String defaultRecipient) {
        return new AdminNotificationService(settingsRepo, emailService, Runnable::run,
                defaultRecipient, "https://www.curtinhonestly.com/", "https://admin.curtinhonestly.com/");
    }

    private AdminNotificationService service() {
        return service(DEFAULT_RECIPIENT);
    }

    private void noStoredSettings() {
        when(settingsRepo.findById(AdminNotificationSettings.SINGLETON_ID)).thenReturn(Optional.empty());
    }

    private AdminNotificationSettings stored(String recipient, String... enabled) {
        AdminNotificationSettings settings = new AdminNotificationSettings();
        settings.setRecipientEmail(recipient);
        settings.setEnabledEvents(new java.util.HashSet<>(Set.of(enabled)));
        when(settingsRepo.findById(AdminNotificationSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        return settings;
    }

    private static User user(String email) {
        User user = new User();
        user.setId("user-1");
        user.setEmail(email);
        user.setCreatedAt(Instant.parse("2026-09-08T02:30:00Z"));
        return user;
    }

    private static Review review() {
        Unit unit = new Unit();
        unit.setId("unit-1");
        unit.setCode("ISYS1000");
        unit.setName("Introduction to Business Information Systems");
        Review review = new Review();
        review.setId("review-1");
        review.setUnit(unit);
        review.setUser(user("alice@student.curtin.edu.au"));
        review.setRating(4);
        review.setWorkload(6);
        review.setReviewText("Solid unit. <script>alert(1)</script> Lectures were clear.");
        review.setCreatedAt(Instant.parse("2026-09-08T02:30:00Z"));
        return review;
    }

    // ---- defaults ----

    @Test
    void withNoSavedSettings_signupsAndReviewsAreOnAndGoToTheConfiguredAddress() {
        noStoredSettings();

        AdminNotificationSettingsDTO dto = service().settings();

        assertThat(dto.recipientEmail()).isEqualTo(DEFAULT_RECIPIENT);
        assertThat(dto.customised()).isFalse();
        assertThat(dto.events()).extracting(AdminNotificationEventDTO::event)
                .containsExactly("NEW_USER", "NEW_REVIEW", "REVIEW_FLAGGED", "UNIT_REQUESTED");
        assertThat(enabledIn(dto)).containsExactlyInAnyOrder("NEW_USER", "NEW_REVIEW");
    }

    @Test
    void settings_reportWhetherSmtpIsConfigured() {
        noStoredSettings();
        when(emailService.isConfigured()).thenReturn(true);

        assertThat(service().settings().mailConfigured()).isTrue();
    }

    @Test
    void userRegistered_emailsTheDefaultRecipientWhenNothingIsSaved() {
        noStoredSettings();

        service().userRegistered(user("newbie@student.curtin.edu.au"));

        verify(emailService).send(eq(DEFAULT_RECIPIENT), subjectCaptor.capture(), textCaptor.capture(), htmlCaptor.capture());
        assertThat(subjectCaptor.getValue()).contains("newbie@student.curtin.edu.au");
        assertThat(textCaptor.getValue()).contains("newbie@student.curtin.edu.au").contains("Unverified");
        assertThat(htmlCaptor.getValue()).contains("https://admin.curtinhonestly.com/operations");
    }

    @Test
    void reviewCreated_includesTheUnitAndEscapesTheReviewTextInTheHtmlPart() {
        noStoredSettings();

        service().reviewCreated(review());

        verify(emailService).send(eq(DEFAULT_RECIPIENT), subjectCaptor.capture(), textCaptor.capture(), htmlCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo("New review: ISYS1000 (4/5)");
        assertThat(textCaptor.getValue())
                .contains("ISYS1000 Introduction to Business Information Systems")
                .contains("alice@student.curtin.edu.au")
                .contains("https://www.curtinhonestly.com/units/ISYS1000");
        // A student's review text is untrusted; the HTML part must never carry it raw.
        assertThat(htmlCaptor.getValue()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void reviewFlagged_isOffByDefault() {
        noStoredSettings();

        service().reviewFlagged(review(), user("bob@student.curtin.edu.au"), "spam");

        verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unitRequested_isOffByDefault() {
        noStoredSettings();
        UnitRequest request = new UnitRequest();
        request.setRequestedCode("COMP9999");

        service().unitRequested(request);

        verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    // ---- saved settings ----

    @Test
    void savedSettings_replaceTheDefaultsEntirely() {
        stored("other@example.test", "REVIEW_FLAGGED");

        AdminNotificationService service = service();
        service.userRegistered(user("newbie@student.curtin.edu.au"));
        service.reviewFlagged(review(), user("bob@student.curtin.edu.au"), "spam");

        // Signups are on by default but were not ticked when the page was saved.
        verify(emailService, never()).send(anyString(), startsWith("New CurtinHonestly signup"), anyString(), anyString());
        verify(emailService).send(eq("other@example.test"), eq("Review flagged: ISYS1000"), textCaptor.capture(), anyString());
        assertThat(textCaptor.getValue()).contains("bob@student.curtin.edu.au").contains("spam");
    }

    @Test
    void savedSettingsWithoutARecipient_fallBackToTheConfiguredAddress() {
        stored(null, "NEW_USER");

        service().userRegistered(user("newbie@student.curtin.edu.au"));

        verify(emailService).send(eq(DEFAULT_RECIPIENT), anyString(), anyString(), anyString());
    }

    @Test
    void anEventNameThatNoLongerExists_isIgnoredRatherThanFatal() {
        stored("other@example.test", "NEW_REVIEW", "SOMETHING_REMOVED");

        assertThatCode(() -> service().reviewCreated(review())).doesNotThrowAnyException();
        verify(emailService).send(eq("other@example.test"), anyString(), anyString(), anyString());
    }

    @Test
    void updateSettings_persistsTheWholeSetAndReturnsTheNewState() {
        noStoredSettings();
        when(settingsRepo.save(any(AdminNotificationSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminNotificationSettingsDTO dto = service().updateSettings("  Owner@Example.test ", List.of("new_review", "UNIT_REQUESTED"));

        ArgumentCaptor<AdminNotificationSettings> saved = ArgumentCaptor.forClass(AdminNotificationSettings.class);
        verify(settingsRepo).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(AdminNotificationSettings.SINGLETON_ID);
        assertThat(saved.getValue().getRecipientEmail()).isEqualTo("Owner@Example.test");
        assertThat(saved.getValue().getEnabledEvents()).containsExactlyInAnyOrder("NEW_REVIEW", "UNIT_REQUESTED");
        // The returned DTO is computed from the same (mocked) repo, so only the
        // shape is checked here; the persisted row above is the real assertion.
        assertThat(dto.events()).hasSize(AdminNotificationEvent.values().length);
    }

    @Test
    void updateSettings_rejectsAnUnusableAddress() {
        assertThatThrownBy(() -> service().updateSettings("not-an-email", List.of("NEW_USER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().updateSettings("   ", List.of("NEW_USER")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(settingsRepo, never()).save(any());
    }

    @Test
    void updateSettings_rejectsAnUnknownEvent() {
        assertThatThrownBy(() -> service().updateSettings("owner@example.test", List.of("NEW_USER", "BOGUS")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOGUS");
        verify(settingsRepo, never()).save(any());
    }

    // ---- never hurting the caller ----

    @Test
    void withNoRecipientAnywhere_anEnabledAlertIsSkippedQuietly() {
        noStoredSettings();

        assertThatCode(() -> service("").userRegistered(user("newbie@student.curtin.edu.au")))
                .doesNotThrowAnyException();
        verifyNoInteractions(emailService);
    }

    @Test
    void aFailureReadingTheSettings_neverReachesTheCaller() {
        when(settingsRepo.findById(anyString())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> service().reviewCreated(review())).doesNotThrowAnyException();
        verifyNoInteractions(emailService);
    }

    @Test
    void insideATransaction_theSendWaitsForTheCommit() {
        noStoredSettings();
        TransactionSynchronizationManager.initSynchronization();

        service().reviewCreated(review());

        verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString());
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(emailService).send(eq(DEFAULT_RECIPIENT), anyString(), anyString(), anyString());
    }

    @Test
    void sendTest_reportsTheAddressItWroteTo() {
        stored("other@example.test", "NEW_USER");

        String sentTo = service().sendTest();

        assertThat(sentTo).isEqualTo("other@example.test");
        verify(emailService).send(eq("other@example.test"), eq("CurtinHonestly admin alerts: test message"),
                textCaptor.capture(), anyString());
        assertThat(textCaptor.getValue()).contains("New signups");
    }

    @Test
    void sendTest_withNoRecipientIsAnError() {
        noStoredSettings();

        assertThatThrownBy(() -> service("").sendTest()).isInstanceOf(IllegalStateException.class);
    }

    private static List<String> enabledIn(AdminNotificationSettingsDTO dto) {
        return dto.events().stream().filter(AdminNotificationEventDTO::enabled).map(AdminNotificationEventDTO::event).toList();
    }
}

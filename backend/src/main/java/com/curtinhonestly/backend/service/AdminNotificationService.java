package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.AdminNotificationEvent;
import com.curtinhonestly.backend.domain.AdminNotificationSettings;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.UnitRequest;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.AdminNotificationEventDTO;
import com.curtinhonestly.backend.dto.AdminNotificationSettingsDTO;
import com.curtinhonestly.backend.repo.AdminNotificationSettingsRepo;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Emails the site owner when something happens on the site: a signup, a review,
 * a flag, a unit request. Which events fire and where they go is set on the admin
 * dashboard (Notifications page) and stored in {@link AdminNotificationSettings};
 * until an admin saves that page once, {@code app.notifications.admin-email} and
 * each event's {@link AdminNotificationEvent#isEnabledByDefault() default} apply.
 *
 * <p>Two rules keep this from ever hurting the student-facing flow it hangs off:
 * <ul>
 *   <li>Sends are deferred to after the calling transaction commits, so a review
 *       that rolls back is never announced, and then handed to the application
 *       task executor, so SMTP latency is never added to the student's request.</li>
 *   <li>{@link #notify} swallows its own failures. An alert is best-effort and
 *       {@link EmailService} already logs what it could not send.</li>
 * </ul>
 *
 * <p>Message text is built while the caller's entities are still attached (before
 * the deferral), so nothing lazy is touched on the executor thread.
 */
@Service
@Slf4j
@Transactional(rollbackOn = Exception.class)
public class AdminNotificationService {

    private static final DateTimeFormatter PERTH_TIME =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a").withZone(ZoneId.of("Australia/Perth"));
    private static final int EXCERPT_LENGTH = 600;

    private final AdminNotificationSettingsRepo settingsRepo;
    private final EmailService emailService;
    private final TaskExecutor taskExecutor;
    private final String defaultRecipient;
    private final String frontendBaseUrl;
    private final String adminBaseUrl;

    public AdminNotificationService(AdminNotificationSettingsRepo settingsRepo,
                                    EmailService emailService,
                                    @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                                    @Value("${app.notifications.admin-email:}") String defaultRecipient,
                                    @Value("${app.frontend-base-url:http://localhost:4200}") String frontendBaseUrl,
                                    @Value("${app.notifications.admin-base-url:http://localhost:4201}") String adminBaseUrl) {
        this.settingsRepo = settingsRepo;
        this.emailService = emailService;
        this.taskExecutor = taskExecutor;
        this.defaultRecipient = blankToNull(defaultRecipient);
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.adminBaseUrl = adminBaseUrl.replaceAll("/+$", "");
    }

    // ---- Settings (admin dashboard) ----

    public AdminNotificationSettingsDTO settings() {
        Optional<AdminNotificationSettings> stored = settingsRepo.findById(AdminNotificationSettings.SINGLETON_ID);
        Resolved resolved = resolve(stored);
        List<AdminNotificationEventDTO> events = Arrays.stream(AdminNotificationEvent.values())
                .map(event -> new AdminNotificationEventDTO(
                        event.name(), event.getLabel(), event.getDescription(), resolved.enabled().contains(event)))
                .toList();
        return new AdminNotificationSettingsDTO(resolved.recipient(), emailService.isConfigured(), stored.isPresent(), events);
    }

    /**
     * Replaces the whole preference set: every event not named is switched off, so
     * the page's tick boxes are the complete truth after a save.
     */
    public AdminNotificationSettingsDTO updateSettings(String recipientEmail, Collection<String> enabledEventNames) {
        String recipient = recipientEmail == null ? "" : recipientEmail.trim();
        if (recipient.isEmpty() || !recipient.contains("@") || !recipient.contains(".") || recipient.length() > 320) {
            throw new IllegalArgumentException("Enter a valid email address for the alerts.");
        }

        Set<String> enabled = new TreeSet<>();
        for (String name : enabledEventNames == null ? List.<String>of() : enabledEventNames) {
            AdminNotificationEvent event = AdminNotificationEvent.fromName(name)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown notification event: " + name));
            enabled.add(event.name());
        }

        AdminNotificationSettings settings = settingsRepo.findById(AdminNotificationSettings.SINGLETON_ID)
                .orElseGet(AdminNotificationSettings::new);
        settings.setRecipientEmail(recipient);
        settings.setEnabledEvents(enabled);
        settings.setUpdatedAt(Instant.now());
        settingsRepo.save(settings);
        log.info("Admin notification settings updated: {} event(s) enabled", enabled.size());
        return settings();
    }

    /** Sends a probe to the current recipient so SMTP can be checked without waiting for a real event. */
    public String sendTest() {
        Resolved resolved = resolve(settingsRepo.findById(AdminNotificationSettings.SINGLETON_ID));
        if (resolved.recipient() == null) {
            throw new IllegalStateException("No recipient is configured for admin alerts.");
        }
        String enabledList = resolved.enabled().isEmpty()
                ? "none"
                : String.join(", ", resolved.enabled().stream().map(AdminNotificationEvent::getLabel).toList());
        String text = """
                This is a test alert from the CurtinHonestly admin dashboard.

                Alerts currently switched on: %s

                Change these at %s/notifications
                """.formatted(enabledList, adminBaseUrl);
        String html = htmlEmail("Test alert",
                "<p>This is a test alert from the CurtinHonestly admin dashboard.</p>"
                        + "<p>Alerts currently switched on: <strong>" + escape(enabledList) + "</strong></p>",
                adminBaseUrl + "/notifications", "Open notification settings");
        deliver(resolved.recipient(), "CurtinHonestly admin alerts: test message", text, html);
        return resolved.recipient();
    }

    // ---- Events ----

    public void userRegistered(User user) {
        String email = orDash(user.getEmail());
        String status = user.isVerifiedStudent() ? "Verified student" : "Unverified";
        String ref = orDash(user.getRegisteredViaRef());
        String when = formatTime(user.getCreatedAt());

        String text = """
                A new account was created on CurtinHonestly.

                Email: %s
                Status: %s
                Referral: %s
                Created: %s

                Manage users: %s/operations
                """.formatted(email, status, ref, when, adminBaseUrl);
        String html = htmlEmail("New signup",
                "<p>A new account was created on CurtinHonestly.</p>"
                        + facts(new String[][]{
                                {"Email", email},
                                {"Status", status},
                                {"Referral", ref},
                                {"Created", when}}),
                adminBaseUrl + "/operations", "Manage users");
        notify(AdminNotificationEvent.NEW_USER, "New CurtinHonestly signup: " + email, text, html);
    }

    public void reviewCreated(Review review) {
        String unitCode = review.getUnit() != null ? orDash(review.getUnit().getCode()) : "unknown";
        String unitName = review.getUnit() != null ? orDash(review.getUnit().getName()) : "";
        String author = review.getUser() != null ? orDash(review.getUser().getEmail()) : "anonymous";
        String rating = review.getRating() + "/5";
        String workload = review.getWorkload() + "/10";
        String grade = review.getFinalGrade() == null ? "Not given" : String.valueOf(review.getFinalGrade());
        String term = review.getTermType() == null ? "-"
                : review.getTermType().name() + (review.getTermYear() == null ? "" : " " + review.getTermYear());
        String professor = orDash(review.getProfessor());
        String body = excerpt(review.getReviewText());
        String unitUrl = frontendBaseUrl + "/units/" + unitCode;
        String when = formatTime(review.getCreatedAt());

        String text = """
                A new review was posted on CurtinHonestly.

                Unit: %s %s
                Rating: %s
                Workload: %s
                Final grade: %s
                Term: %s
                Lecturer: %s
                Author: %s
                Posted: %s

                %s

                Unit page: %s
                Moderate: %s/operations
                """.formatted(unitCode, unitName, rating, workload, grade, term, professor, author, when,
                body.isEmpty() ? "(no review text)" : body, unitUrl, adminBaseUrl);
        String html = htmlEmail("New review: " + escape(unitCode),
                "<p>A new review was posted on CurtinHonestly.</p>"
                        + facts(new String[][]{
                                {"Unit", unitCode + " " + unitName},
                                {"Rating", rating},
                                {"Workload", workload},
                                {"Final grade", grade},
                                {"Term", term},
                                {"Lecturer", professor},
                                {"Author", author},
                                {"Posted", when}})
                        + quote(body.isEmpty() ? "(no review text)" : body)
                        + "<p><a href=\"" + escape(unitUrl) + "\">Open the unit page</a></p>",
                adminBaseUrl + "/operations", "Moderate reviews");
        notify(AdminNotificationEvent.NEW_REVIEW, "New review: " + unitCode + " (" + rating + ")", text, html);
    }

    public void reviewFlagged(Review review, User flaggedBy, String reason) {
        String unitCode = review.getUnit() != null ? orDash(review.getUnit().getCode()) : "unknown";
        String reporter = flaggedBy != null ? orDash(flaggedBy.getEmail()) : "unknown";
        String why = orDash(reason);
        String body = excerpt(review.getReviewText());

        String text = """
                A review was flagged on CurtinHonestly.

                Unit: %s
                Reported by: %s
                Reason: %s
                Review ID: %s

                %s

                Review flags: %s/operations
                """.formatted(unitCode, reporter, why, orDash(review.getId()),
                body.isEmpty() ? "(no review text)" : body, adminBaseUrl);
        String html = htmlEmail("Review flagged: " + escape(unitCode),
                "<p>A review was flagged on CurtinHonestly.</p>"
                        + facts(new String[][]{
                                {"Unit", unitCode},
                                {"Reported by", reporter},
                                {"Reason", why},
                                {"Review ID", orDash(review.getId())}})
                        + quote(body.isEmpty() ? "(no review text)" : body),
                adminBaseUrl + "/operations", "Review the flags");
        notify(AdminNotificationEvent.REVIEW_FLAGGED, "Review flagged: " + unitCode, text, html);
    }

    public void unitRequested(UnitRequest request) {
        String code = orDash(request.getRequestedCode());
        String note = orDash(request.getNote());

        String text = """
                A student asked for a unit that is not in the catalogue.

                Requested: %s
                Note: %s

                Unit requests: %s/operations
                """.formatted(code, note, adminBaseUrl);
        String html = htmlEmail("Unit requested: " + escape(code),
                "<p>A student asked for a unit that is not in the catalogue.</p>"
                        + facts(new String[][]{{"Requested", code}, {"Note", note}}),
                adminBaseUrl + "/operations", "See unit requests");
        notify(AdminNotificationEvent.UNIT_REQUESTED, "Unit requested: " + code, text, html);
    }

    // ---- Core ----

    /**
     * Queues one alert if {@code event} is switched on. Never throws: a failure to
     * read the settings or to hand off the send is logged and the caller's work
     * proceeds unaffected.
     */
    void notify(AdminNotificationEvent event, String subject, String textBody, String htmlBody) {
        try {
            Resolved resolved = resolve(settingsRepo.findById(AdminNotificationSettings.SINGLETON_ID));
            if (!resolved.enabled().contains(event)) {
                log.debug("Admin alert {} is switched off; not sending", event);
                return;
            }
            if (resolved.recipient() == null) {
                log.warn("Admin alert {} is switched on but no recipient is configured "
                        + "(set ADMIN_NOTIFICATION_EMAIL or save one on the admin Notifications page)", event);
                return;
            }
            afterCommit(() -> deliver(resolved.recipient(), subject, textBody, htmlBody));
        } catch (RuntimeException e) {
            log.error("Could not queue admin alert {}: {}", event, e.getMessage());
        }
    }

    private void deliver(String to, String subject, String textBody, String htmlBody) {
        try {
            taskExecutor.execute(() -> emailService.send(to, subject, textBody, htmlBody));
        } catch (RuntimeException e) {
            log.error("Could not hand admin alert '{}' to the task executor: {}", subject, e.getMessage());
        }
    }

    // Same shape as RecommendationService.invalidateAfterCommit: inside a
    // transaction the work waits for the commit, outside one it runs at once.
    private static void afterCommit(Runnable work) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    work.run();
                }
            });
        } else {
            work.run();
        }
    }

    /** The recipient and enabled set in force right now. */
    private record Resolved(String recipient, Set<AdminNotificationEvent> enabled) {}

    private Resolved resolve(Optional<AdminNotificationSettings> stored) {
        if (stored.isEmpty()) {
            Set<AdminNotificationEvent> defaults = EnumSet.noneOf(AdminNotificationEvent.class);
            for (AdminNotificationEvent event : AdminNotificationEvent.values()) {
                if (event.isEnabledByDefault()) {
                    defaults.add(event);
                }
            }
            return new Resolved(defaultRecipient, defaults);
        }
        AdminNotificationSettings settings = stored.get();
        Set<AdminNotificationEvent> enabled = EnumSet.noneOf(AdminNotificationEvent.class);
        for (String name : settings.getEnabledEvents()) {
            // A name that no longer matches a constant (a monitor that was removed)
            // is simply dropped rather than failing every alert.
            AdminNotificationEvent.fromName(name).ifPresent(enabled::add);
        }
        String recipient = blankToNull(settings.getRecipientEmail());
        return new Resolved(recipient != null ? recipient : defaultRecipient, enabled);
    }

    // ---- Formatting ----

    private static String formatTime(Instant instant) {
        return instant == null ? "-" : PERTH_TIME.format(instant) + " (Perth)";
    }

    private static String excerpt(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.length() <= EXCERPT_LENGTH) return trimmed;
        return trimmed.substring(0, EXCERPT_LENGTH - 1).trim() + "…";
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** All user-supplied text goes through here before landing in the HTML part. */
    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private static String facts(String[][] rows) {
        StringBuilder sb = new StringBuilder("<table style=\"border-collapse: collapse; margin: 12px 0;\">");
        for (String[] row : rows) {
            sb.append("<tr><td style=\"padding: 4px 12px 4px 0; color: #616e7c; vertical-align: top;\">")
                    .append(escape(row[0]))
                    .append("</td><td style=\"padding: 4px 0; vertical-align: top;\">")
                    .append(escape(row[1]))
                    .append("</td></tr>");
        }
        return sb.append("</table>").toString();
    }

    private static String quote(String text) {
        return "<blockquote style=\"margin: 12px 0; padding: 10px 14px; background: #f5f7fa; border-left: 4px solid #0a2540; "
                + "white-space: pre-wrap;\">" + escape(text) + "</blockquote>";
    }

    // Same inline-styled shape as the verification emails so the two look related.
    private static String htmlEmail(String heading, String bodyHtml, String link, String buttonLabel) {
        return """
                <!doctype html>
                <html lang="en"><body style="font-family: Arial, Helvetica, sans-serif; color: #1f2933; line-height: 1.5;">
                <h2 style="margin: 0 0 12px; font-size: 18px;">%s</h2>
                %s
                <p style="margin: 24px 0;">
                  <a href="%s" style="display: inline-block; padding: 12px 20px; background: #0a2540; color: #ffffff; text-decoration: none; border-radius: 6px; font-weight: bold;">%s</a>
                </p>
                <p style="color: #616e7c; font-size: 13px;">You are receiving this because admin alerts are switched on in the CurtinHonestly admin dashboard. Turn individual alerts off on its Notifications page.</p>
                </body></html>
                """.formatted(heading, bodyHtml, escape(link), escape(buttonLabel));
    }
}

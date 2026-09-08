package com.curtinhonestly.backend.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * The single row of admin alert preferences. It exists only once an admin has
 * saved the Notifications page; until then {@code AdminNotificationService} runs on
 * the configured defaults. Never exposed through a public endpoint: the recipient
 * address and the toggles are visible to ROLE_ADMIN only.
 */
@Entity
@Table(name = "admin_notification_settings")
@Getter
@Setter
@NoArgsConstructor
public class AdminNotificationSettings {

    /** There is exactly one settings row, and this is its id. */
    public static final String SINGLETON_ID = "default";

    @Id
    @Column(length = 40)
    private String id = SINGLETON_ID;

    // Where the alerts go. Null means "use app.notifications.admin-email".
    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    // Stored as the enum's name in a plain VARCHAR, deliberately not @Enumerated:
    // Hibernate writes a CHECK constraint listing the enum's values for an
    // @Enumerated(STRING) collection, and ddl-auto=update never alters it (see the
    // V8 migration for the roles table). A plain string column lets a new
    // AdminNotificationEvent ship without a migration.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "admin_notification_enabled_events", joinColumns = @JoinColumn(name = "settings_id"))
    @Column(name = "event", nullable = false, length = 60)
    private Set<String> enabledEvents = new HashSet<>();

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant updatedAt = Instant.now();
}

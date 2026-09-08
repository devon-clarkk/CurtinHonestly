package com.curtinhonestly.backend.domain;

import java.util.Optional;

/**
 * Site events an admin can be emailed about. Each one is a tick box on the admin
 * dashboard's Notifications page; the enum is the complete list of boxes.
 *
 * <p>To add a monitor: add a constant here, then call
 * {@code AdminNotificationService.notify(...)} from the code path that produces the
 * event. Nothing else changes: the dashboard renders whatever this enum contains,
 * and the settings table stores event names as plain strings (no enum check
 * constraint), so a new constant needs no migration.
 */
public enum AdminNotificationEvent {

    NEW_USER("New signups", "A student creates an account.", true),
    NEW_REVIEW("New reviews", "A student posts a review.", true),
    REVIEW_FLAGGED("Flagged reviews", "A student reports a review as inappropriate.", false),
    UNIT_REQUESTED("Unit requests", "A student asks for a unit that is not in the catalogue.", false);

    private final String label;
    private final String description;
    private final boolean enabledByDefault;

    AdminNotificationEvent(String label, String description, boolean enabledByDefault) {
        this.label = label;
        this.description = description;
        this.enabledByDefault = enabledByDefault;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /** What applies until an admin saves the page for the first time. */
    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    /** Lenient lookup: an unknown or removed name resolves to empty instead of throwing. */
    public static Optional<AdminNotificationEvent> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (AdminNotificationEvent event : values()) {
            if (event.name().equalsIgnoreCase(name.trim())) {
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }
}

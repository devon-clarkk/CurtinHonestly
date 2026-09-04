package com.curtinhonestly.backend.domain;

import lombok.Getter;

/**
 * Lifecycle of a {@link ClubEvent}. Only PUBLISHED rows are ever shown on the
 * public site. Trusted clubs publish straight to PUBLISHED; untrusted clubs
 * land in PENDING until an admin approves.
 */
@Getter
public enum ClubEventStatus {
    DRAFT("Draft"),
    PENDING("Pending approval"),
    PUBLISHED("Published"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled");

    private final String displayName;

    ClubEventStatus(String displayName) {
        this.displayName = displayName;
    }

    public static ClubEventStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Status is required.");
        }
        try {
            return ClubEventStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status: " + raw.trim());
        }
    }
}

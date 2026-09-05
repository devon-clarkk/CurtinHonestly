package com.curtinhonestly.backend.domain;

import lombok.Getter;

/** What kind of session a {@link ClubEvent} is. Declaration order is the display order in filters. */
@Getter
public enum ClubEventKind {
    REVISION_SESSION("Revision session"),
    TUTORING("Tutoring"),
    WORKSHOP("Workshop"),
    INFO_SESSION("Info session"),
    SOCIAL("Social"),
    OTHER("Other");

    private final String displayName;

    ClubEventKind(String displayName) {
        this.displayName = displayName;
    }

    /** Parses a kind by enum name, case-insensitively. Anything else is a 400-mapped error. */
    public static ClubEventKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Choose what kind of event this is.");
        }
        try {
            return ClubEventKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown event kind: " + raw.trim());
        }
    }
}

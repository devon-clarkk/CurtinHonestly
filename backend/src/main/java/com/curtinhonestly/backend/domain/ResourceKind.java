package com.curtinhonestly.backend.domain;

import lombok.Getter;

/**
 * What kind of link a {@link UnitResourceLink} is. The public unit page groups
 * resources by kind, in this declaration order, so keep the most useful kinds
 * (communities first) at the top.
 */
@Getter
public enum ResourceKind {
    DISCORD("Discord server"),
    CLUB("Club or society"),
    STUDY_GROUP("Study group"),
    NOTES("Notes"),
    PAST_PAPERS("Past papers"),
    TEXTBOOK("Textbook"),
    VIDEO("Video"),
    WEBSITE("Website"),
    OTHER("Other");

    private final String displayName;

    ResourceKind(String displayName) {
        this.displayName = displayName;
    }

    /** Parses a kind by enum name, case-insensitively. Anything else is a 400-mapped error. */
    public static ResourceKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Choose what kind of resource this is.");
        }
        try {
            return ResourceKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown resource kind: " + raw.trim());
        }
    }
}

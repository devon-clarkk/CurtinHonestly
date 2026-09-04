package com.curtinhonestly.backend.domain;

/**
 * What a member may do inside their club's portal. OWNERs also edit the club
 * profile (description, website, logo, contact); EDITORs manage events only.
 */
public enum ClubMemberRole {
    OWNER,
    EDITOR;

    public static ClubMemberRole parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EDITOR;
        }
        try {
            return ClubMemberRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown member role: " + raw.trim());
        }
    }
}

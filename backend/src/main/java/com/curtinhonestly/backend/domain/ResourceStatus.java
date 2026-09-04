package com.curtinhonestly.backend.domain;

/**
 * Moderation state of a {@link UnitResourceLink}. Only APPROVED rows are ever
 * shown on the public site; PENDING rows are student suggestions awaiting an
 * admin; REJECTED rows are kept so an admin can see what was already declined.
 */
public enum ResourceStatus {
    APPROVED,
    PENDING,
    REJECTED;

    public static ResourceStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Status is required.");
        }
        try {
            return ResourceStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status: " + raw.trim());
        }
    }
}

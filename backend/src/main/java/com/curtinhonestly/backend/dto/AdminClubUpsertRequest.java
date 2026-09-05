package com.curtinhonestly.backend.dto;

/**
 * Create or edit a club from the admin app. A blank {@code slug} on create is
 * derived from the name. {@code trusted} and {@code active} default to false
 * and true respectively when null.
 */
public record AdminClubUpsertRequest(
        String name,
        String slug,
        String description,
        String websiteUrl,
        String logoUrl,
        String contactEmail,
        Boolean trusted,
        Boolean active
) {}

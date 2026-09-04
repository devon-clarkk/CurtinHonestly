package com.curtinhonestly.backend.dto;

/**
 * A club as seen by one of its members in the portal: the profile fields they
 * may edit, whether publishing is immediate ({@code trusted}) and the caller's
 * own role in it.
 */
public record ClubPortalClubDTO(
        String id,
        String name,
        String slug,
        String description,
        String websiteUrl,
        String logoUrl,
        String contactEmail,
        boolean trusted,
        boolean active,
        String role
) {}

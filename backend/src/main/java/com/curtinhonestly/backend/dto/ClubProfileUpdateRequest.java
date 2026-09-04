package com.curtinhonestly.backend.dto;

/** The fields a club OWNER may change from the portal. Name, slug and trust stay with admins. */
public record ClubProfileUpdateRequest(
        String description,
        String websiteUrl,
        String logoUrl,
        String contactEmail
) {}

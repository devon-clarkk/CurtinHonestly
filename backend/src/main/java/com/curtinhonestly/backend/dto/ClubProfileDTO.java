package com.curtinhonestly.backend.dto;

import java.util.List;

/** The public club page: profile plus its upcoming published events, soonest first. */
public record ClubProfileDTO(
        String id,
        String name,
        String slug,
        String description,
        String websiteUrl,
        String logoUrl,
        String contactEmail,
        List<ClubEventDTO> upcomingEvents
) {}

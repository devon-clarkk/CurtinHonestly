package com.curtinhonestly.backend.dto;

/** One club in the public /clubs directory, with how many upcoming events it has. */
public record ClubSummaryDTO(
        String id,
        String name,
        String slug,
        String description,
        String websiteUrl,
        String logoUrl,
        int upcomingEventCount
) {}

package com.curtinhonestly.backend.dto;

import java.time.Instant;

/**
 * One published event as shown on the public site. Times are UTC instants;
 * the frontend renders them in Australia/Perth. {@code nextStartsAt} is the
 * start to display: the event's own start, or for a recurring event whose
 * first start has passed, the next weekly projection of it. {@code scopeLabel}
 * says which unit pages carry it ("This unit", "All COMP1 and ISAD1 units").
 */
public record ClubEventDTO(
        String id,
        String clubId,
        String clubName,
        String clubSlug,
        String title,
        String description,
        String kind,
        String kindLabel,
        Instant startsAt,
        Instant endsAt,
        Instant nextStartsAt,
        String location,
        boolean online,
        String link,
        boolean recurring,
        String recurrenceNote,
        String scopeLabel,
        String targetUnitCode,
        String targetUnitName,
        boolean showOnHome,
        int viewCount
) {}

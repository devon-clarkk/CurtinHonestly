package com.curtinhonestly.backend.dto;

import java.time.Instant;

/**
 * Create or edit an event, from the club portal or the admin app. Times are
 * ISO-8601 instants (UTC); the frontend converts from Perth local time.
 *
 * Targeting works like resources: a non-blank {@code unitCode} targets that
 * one unit and the rule fields are ignored; otherwise the row is a rule built
 * from {@code codePrefixes} (comma separated), {@code faculty} and
 * {@code level}, all optional. A rule with no criteria shows on every unit
 * page. {@code showOnHome} adds the event to the home page strip.
 */
public record ClubEventUpsertRequest(
        String title,
        String description,
        String kind,
        Instant startsAt,
        Instant endsAt,
        String location,
        Boolean online,
        String link,
        Boolean recurring,
        String recurrenceNote,
        String unitCode,
        String codePrefixes,
        String faculty,
        String level,
        Boolean showOnHome
) {}

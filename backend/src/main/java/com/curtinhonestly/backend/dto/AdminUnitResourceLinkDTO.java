package com.curtinhonestly.backend.dto;

import java.time.Instant;

/**
 * The admin view of a resource row. {@code submittedBy} is only ever
 * "student", "admin" or null (account deleted); an email is never exposed.
 * {@code faculty} and {@code level} are enum names, with display labels
 * alongside, so the admin UI can round-trip them into the edit form.
 */
public record AdminUnitResourceLinkDTO(
        String id,
        String title,
        String url,
        String description,
        String kind,
        String kindLabel,
        String targetUnitCode,
        String targetUnitName,
        String codePrefixes,
        String faculty,
        String facultyLabel,
        String level,
        String levelLabel,
        String scopeLabel,
        String status,
        int sortOrder,
        int clickCount,
        String submittedBy,
        String submitterNote,
        Instant createdAt,
        Instant approvedAt
) {}

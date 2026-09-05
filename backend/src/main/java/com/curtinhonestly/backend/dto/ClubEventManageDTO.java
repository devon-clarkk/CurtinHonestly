package com.curtinhonestly.backend.dto;

import java.time.Instant;

/**
 * An event as seen by the people who manage it: the club portal and the admin
 * app. Carries every editable field plus status and audit columns.
 * {@code createdByEmail} is only filled for admins.
 */
public record ClubEventManageDTO(
        String id,
        String clubId,
        String clubName,
        String clubSlug,
        boolean clubTrusted,
        String title,
        String description,
        String kind,
        String kindLabel,
        Instant startsAt,
        Instant endsAt,
        String location,
        boolean online,
        String link,
        boolean recurring,
        String recurrenceNote,
        String targetUnitCode,
        String targetUnitName,
        String codePrefixes,
        String faculty,
        String level,
        String scopeLabel,
        boolean showOnHome,
        String status,
        String statusLabel,
        String rejectionReason,
        String createdByEmail,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        int viewCount
) {}

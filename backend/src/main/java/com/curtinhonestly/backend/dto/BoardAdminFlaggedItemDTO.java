package com.curtinhonestly.backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * One flagged target (a thread or a post) for the admin queue, with its flag
 * count and the reasons reporters gave. targetType is "THREAD" or "POST".
 */
public record BoardAdminFlaggedItemDTO(
        String targetType,
        String targetId,
        String threadId,
        String threadTitle,
        String unitCode,
        String body,
        String authorPseudonym,
        String authorEmail,
        long flagCount,
        List<String> reasons,
        Instant latestFlagAt,
        Instant createdAt
) {}

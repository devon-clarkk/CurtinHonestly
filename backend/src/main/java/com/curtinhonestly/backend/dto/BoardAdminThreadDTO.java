package com.curtinhonestly.backend.dto;

import com.curtinhonestly.backend.domain.BoardScope;

import java.time.Instant;

// Moderation view of a thread. Carries the author email like AdminReviewDTO so
// repeat offenders can be found on the Operations page; never served publicly.
public record BoardAdminThreadDTO(
        String id,
        BoardScope scope,
        String unitCode,
        String unitName,
        String title,
        String body,
        String authorPseudonym,
        String authorEmail,
        boolean authorVerified,
        int replyCount,
        boolean pinned,
        boolean locked,
        long flagCount,
        Instant createdAt,
        Instant editedAt,
        Instant lastActivityAt,
        Instant deletedAt
) {}

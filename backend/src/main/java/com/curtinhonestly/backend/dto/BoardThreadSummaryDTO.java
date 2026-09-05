package com.curtinhonestly.backend.dto;

import com.curtinhonestly.backend.domain.BoardScope;

import java.time.Instant;

/**
 * One row in a thread list. unitCode/unitName are null for general threads
 * and populated for unit threads, so the cross-board recent list can link
 * back to the unit.
 */
public record BoardThreadSummaryDTO(
        String id,
        BoardScope scope,
        String unitCode,
        String unitName,
        String title,
        String excerpt,
        BoardAuthorDTO author,
        int replyCount,
        boolean pinned,
        boolean locked,
        Instant createdAt,
        Instant lastActivityAt
) {}

package com.curtinhonestly.backend.dto;

import java.time.Instant;

public record BoardAdminPostDTO(
        String id,
        String threadId,
        String threadTitle,
        String unitCode,
        String body,
        String authorPseudonym,
        String authorEmail,
        boolean authorVerified,
        long flagCount,
        Instant createdAt,
        Instant editedAt,
        Instant deletedAt
) {}

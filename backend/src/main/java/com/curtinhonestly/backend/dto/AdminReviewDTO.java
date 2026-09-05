package com.curtinhonestly.backend.dto;

import java.time.Instant;
import java.util.List;

// The full stored review, the same fields the public unit page shows plus the
// moderation context (author, flags). Used by both the paged list and the
// single-review endpoint; the list excerpt is a frontend concern.
public record AdminReviewDTO(
        String id,
        String unitCode,
        String unitName,
        String authorEmail,
        String authorId,
        boolean authorVerified,
        int rating,
        Integer finalGrade,
        String reviewText,
        String termType,
        Integer termYear,
        String professor,
        int workload,
        boolean hasExam,
        boolean wouldTakeAgain,
        List<String> tags,
        int likeCount,
        long flagCount,
        Instant createdAt
) {}

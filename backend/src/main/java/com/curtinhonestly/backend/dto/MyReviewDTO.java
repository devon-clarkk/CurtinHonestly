package com.curtinhonestly.backend.dto;

import com.curtinhonestly.backend.domain.ReviewTag;

import java.time.Instant;
import java.util.Set;

public record MyReviewDTO(
        String id,
        String unitCode,
        String unitName,
        int rating,
        Integer finalGrade,
        String reviewText,
        String semesterTaken,
        String professor,
        int workload,
        boolean hasExam,
        boolean wouldTakeAgain,
        Set<ReviewTag> tags,
        int likeCount,
        Instant createdAt
) {}

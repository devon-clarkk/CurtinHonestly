package com.curtinhonestly.backend.dto;

import java.time.Instant;

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
        int likeCount,
        Instant createdAt
) {}

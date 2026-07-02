package com.curtinhonestly.backend.dto;

import java.time.Instant;

public record MyReviewDTO(
        String id,
        String unitCode,
        String unitName,
        int rating,
        String reviewText,
        String semesterTaken,
        Instant createdAt
) {}

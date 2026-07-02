package com.curtinhonestly.backend.dto;

import java.time.Instant;

public record AdminReviewDTO(
        String id,
        String unitCode,
        String authorEmail,
        int rating,
        String reviewText,
        Instant createdAt
) {}

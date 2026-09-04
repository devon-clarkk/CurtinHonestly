package com.curtinhonestly.backend.dto;

public record AdminLikedReviewDTO(
        String id,
        String unitCode,
        int likeCount,
        String excerpt
) {}

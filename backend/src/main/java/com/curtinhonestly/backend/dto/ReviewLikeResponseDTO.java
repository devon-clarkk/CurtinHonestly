package com.curtinhonestly.backend.dto;

public record ReviewLikeResponseDTO(
        String reviewId,
        int likeCount,
        boolean likedByCurrentUser
) {}

package com.curtinhonestly.backend.dto;

// Email is masked (d***@student.curtin.edu.au) so the leaderboard can be shown
// on a shared screen without exposing an address.
public record AdminReviewerDTO(
        String maskedEmail,
        long reviewCount,
        long likesReceived
) {}

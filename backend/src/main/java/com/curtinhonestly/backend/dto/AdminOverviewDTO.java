package com.curtinhonestly.backend.dto;

public record AdminOverviewDTO(
        long totalUsers,
        long totalReviews,
        long totalUnits,
        long reviewsLast7Days,
        long usersLast7Days,
        double averageRating
) {}

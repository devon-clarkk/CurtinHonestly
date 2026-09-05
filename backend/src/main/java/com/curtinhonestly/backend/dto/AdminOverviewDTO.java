package com.curtinhonestly.backend.dto;

import java.util.List;

// Everything the admin Overview page renders in one round trip. Counts are
// whole-table aggregates; the "last7"/"prior7" pairs let the UI show a delta
// without a second request.
public record AdminOverviewDTO(
        long totalUsers,
        long verifiedUsers,
        long bannedUsers,
        long totalReviews,
        long reviewsWithText,
        long unitsWithAtLeastOneReview,
        long totalUnits,
        double coverageRatio,
        long pendingUnitRequests,
        long openFlaggedReviews,
        long totalLikes,
        long usersLast7Days,
        long usersPrior7Days,
        long reviewsLast7Days,
        long reviewsPrior7Days,
        long unverifiedUsersLast7Days,
        double verificationRate,
        List<TimeSeriesPointDTO> signupsAndReviewsOverTime,
        List<AdminUnitLeaderDTO> topUnits,
        List<AdminRequestedUnitDTO> mostRequestedUnits
) {}

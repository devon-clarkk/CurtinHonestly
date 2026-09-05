package com.curtinhonestly.backend.dto;

import java.util.List;

// The time series follows the requested period; every other figure covers the
// whole dataset so the distributions stay meaningful on a small site.
public record AdminAnalyticsDTO(
        int periodDays,
        List<TimeSeriesPointDTO> signupsAndReviewsOverTime,
        long totalUsers,
        long totalReviews,
        long activeReviewers,
        double verificationRate,
        double activeReviewerShare,
        double reviewsPerActiveReviewer,
        List<AdminDistributionBucketDTO> ratingDistribution,
        List<AdminDistributionBucketDTO> workloadDistribution,
        double averageWorkload,
        double wouldTakeAgainRatio,
        double averageReviewTextLength,
        double gradeShare,
        List<AdminFacultyBreakdownDTO> facultyBreakdown,
        List<AdminTermCountDTO> reviewsByTerm,
        List<AdminLikedReviewDTO> mostLikedReviews,
        List<AdminReviewerDTO> mostActiveReviewers
) {}

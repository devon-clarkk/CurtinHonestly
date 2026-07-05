package com.curtinhonestly.backend.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class UnitSummaryDTO {
    private String code;
    private String name;
    private String faculty;
    private String level;

    // Review dependent values (Calculated from the current reviews)
    private int numberOfReviews;
    private double averageRating;
    private double wouldTakeAgainRatio;

    // Null when no reviews exist; used for sitemap lastmod
    private Instant latestReviewAt;
}

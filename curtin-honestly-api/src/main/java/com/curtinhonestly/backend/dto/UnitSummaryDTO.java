package com.curtinhonestly.backend.dto;

import lombok.Data;


@Data
public class UnitSummaryDTO {
    private String code;
    private String name;
    private String faculty;

    // Review dependent values (Calculated from the current reviews)
    private int numberOfReviews;
    private double averageRating;
    private double wouldTakeAgainPercentage;
}

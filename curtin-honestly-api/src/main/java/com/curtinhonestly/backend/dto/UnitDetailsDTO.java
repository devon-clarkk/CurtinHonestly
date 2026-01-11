package com.curtinhonestly.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class UnitDetailsDTO {
    private String code;
    private String name;
    private String description;
    private String faculty;

    // Review dependent values (Calculated from the current reviews)
    private int numberOfReviews;
    private double averageRating;
    private double averageWorkload;
    private double averageFinalGrade;
    private double wouldTakeAgainRatio;

    private List<ReviewDTO> reviews;
}

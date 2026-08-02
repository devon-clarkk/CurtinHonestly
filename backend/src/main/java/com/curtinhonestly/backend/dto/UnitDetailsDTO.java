package com.curtinhonestly.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class UnitDetailsDTO {
    private String code;
    private String name;
    private String description;
    private String unitLink;
    private String faculty;
    private String level;

    private String area;
    private String fieldOfEducation;
    private Integer credits;
    private Integer contactHours;
    private String resultType;

    private List<UnitTuitionPatternDTO> tuitionPatterns;
    private List<UnitPrerequisiteGroupDTO> prerequisiteGroups;

    // Overall eligibility across all prerequisite groups (AND'd), populated
    // only for authenticated requests. Null when anonymous, when there are no
    // prerequisite groups, or when eligibility can't be fully determined
    // (see UnitPrerequisiteGroupDTO.unverifiable).
    private Boolean prerequisitesEligible;

    // Review dependent values (Calculated from the current reviews)
    private int numberOfReviews;
    private double averageRating;
    private double averageWorkload;
    private double averageFinalGrade;
    private double wouldTakeAgainRatio;
    private List<TagSummaryDTO> tagSummary;

    private List<ReviewDTO> reviews;
}

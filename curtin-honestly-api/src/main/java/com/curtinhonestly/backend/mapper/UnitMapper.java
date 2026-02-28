package com.curtinhonestly.backend.mapper;

import com.curtinhonestly.backend.domain.*;
import com.curtinhonestly.backend.dto.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class UnitMapper {

    public static UnitSummaryDTO toSummaryDTO(Unit unit)
    {
        UnitSummaryDTO dto = new UnitSummaryDTO();

        // Base unit information
        dto.setName(unit.getName() != null ? unit.getName() : "");
        dto.setCode(unit.getCode() != null ? unit.getCode() : "");
        dto.setFaculty(unit.getFaculty() != null ? unit.getFaculty().getDisplayName() : "");

        List<Review> reviews = unit.getReviews() != null ? unit.getReviews() : new ArrayList<>();

        // Review-based information
        dto.setNumberOfReviews(reviews.size());
        dto.setAverageRating(calculateAverageRating(reviews));
        dto.setWouldTakeAgainRatio(calculateWouldTakeAgainRatio(reviews));
        return dto;
    }

    public static UnitDetailsDTO toDetailsDTO(Unit unit)
    {

        UnitDetailsDTO dto = new UnitDetailsDTO();

        // Base unit information
        dto.setCode(unit.getCode() != null ? unit.getCode() : "");
        dto.setName(unit.getName() != null ? unit.getName() : "");
        dto.setDescription(unit.getDescription() != null ? unit.getDescription() : "");
        dto.setUnitLink(unit.getUnitLink() != null ? unit.getUnitLink() : "");
        dto.setFaculty(unit.getFaculty() != null ? unit.getFaculty().getDisplayName() : "");

        dto.setArea(unit.getArea());
        dto.setFieldOfEducation(unit.getFieldOfEducation());
        dto.setCredits(unit.getCredits());
        dto.setContactHours(unit.getContactHours());
        dto.setResultType(unit.getResultType());

        dto.setTuitionPatterns(unit.getTuitionPatterns() != null ?
                unit.getTuitionPatterns().stream()
                        .map(UnitMapper::toTuitionPatternDTO)
                        .collect(Collectors.toList()) : new ArrayList<>());

        dto.setPrerequisiteGroups(unit.getPrerequisiteGroups() != null ?
                unit.getPrerequisiteGroups().stream()
                        .map(UnitMapper::toPrerequisiteGroupDTO)
                        .collect(Collectors.toList()) : new ArrayList<>());

        List<Review> reviews = unit.getReviews() != null ? unit.getReviews() : new ArrayList<>();

        // Review-based information
        dto.setNumberOfReviews(reviews.size());
        dto.setAverageRating(calculateAverageRating(reviews));
        dto.setAverageWorkload(calculateAverageWorkload(reviews));
        dto.setAverageFinalGrade(calculateAverageFinalGrade(reviews));
        dto.setWouldTakeAgainRatio(calculateWouldTakeAgainRatio(reviews));

        // Unit reviews in ReviewDTO format.
        dto.setReviews(ReviewMapper.mapToDTOs(reviews));
        return dto;
    }

    private static UnitTuitionPatternDTO toTuitionPatternDTO(UnitTuitionPattern pattern) {
        UnitTuitionPatternDTO dto = new UnitTuitionPatternDTO();
        dto.setType(pattern.getType());
        dto.setDuration(pattern.getDuration());
        return dto;
    }

    private static UnitPrerequisiteGroupDTO toPrerequisiteGroupDTO(UnitPrerequisiteGroup group) {
        UnitPrerequisiteGroupDTO dto = new UnitPrerequisiteGroupDTO();
        dto.setGroupName(group.getGroupName());
        dto.setRequirement(group.getRequirement());
        dto.setPosition(group.getPosition());
        dto.setOptions(group.getOptions() != null ?
                group.getOptions().stream()
                        .map(UnitMapper::toPrerequisiteOptionDTO)
                        .collect(Collectors.toList()) : new ArrayList<>());
        return dto;
    }

    private static UnitPrerequisiteOptionDTO toPrerequisiteOptionDTO(UnitPrerequisiteOption option) {
        UnitPrerequisiteOptionDTO dto = new UnitPrerequisiteOptionDTO();
        dto.setCode(option.getCode());
        dto.setTitle(option.getTitle());
        return dto;
    }



    private static double calculateAverageRating(List<Review> reviews) {
        if (reviews.isEmpty()) return 0.0;

        double avg = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0);

        return roundTo1Decimal(avg);
    }


    private static double calculateWouldTakeAgainRatio(List<Review> reviews) {
        if (reviews.isEmpty()) return 0.0;

        long positiveCount = reviews.stream()
                .filter(Review::isWouldTakeAgain)
                .count();

        double ratio = (positiveCount * 100.0) / reviews.size();
        return roundTo1Decimal(ratio);
    }

    private static double calculateAverageWorkload(List<Review> reviews) {
        if (reviews.isEmpty()) return 0.0;

        double avg = reviews.stream()
                .mapToInt(Review::getWorkload)
                .average()
                .orElse(0);

        return roundTo1Decimal(avg);
    }

    private static double calculateAverageFinalGrade(List<Review> reviews) {
        if (reviews.isEmpty()) return 0;

        double avg = reviews.stream()
                .map(Review::getFinalGrade)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);

        return Math.round(avg * 10.0) / 10.0;
    }

    private static double roundTo1Decimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

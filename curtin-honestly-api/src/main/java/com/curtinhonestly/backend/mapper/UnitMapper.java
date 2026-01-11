package com.curtinhonestly.backend.mapper;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.dto.UnitDetailsDTO;
import com.curtinhonestly.backend.dto.UnitSummaryDTO;

import java.util.List;
import java.util.Objects;

public class UnitMapper {

    public static UnitSummaryDTO toSummaryDTO(Unit unit)
    {
        UnitSummaryDTO dto = new UnitSummaryDTO();

        // Base unit information
        dto.setName(unit.getName());
        dto.setCode(unit.getCode());
        dto.setFaculty(unit.getFaculty());



        // Review-based information
        dto.setNumberOfReviews(unit.getReviews().size());
        dto.setAverageRating(calculateAverageRating(unit.getReviews()));
        dto.setWouldTakeAgainRatio(calculateWouldTakeAgainRatio(unit.getReviews()));
        return dto;
    }

    public static UnitDetailsDTO toDetailsDTO(Unit unit)
    {

        UnitDetailsDTO dto = new UnitDetailsDTO();

        // Base unit information
        dto.setCode(unit.getCode());
        dto.setName(unit.getName());
        dto.setDescription(unit.getDescription());
        dto.setFaculty(unit.getFaculty());

        // Review-based information
        dto.setNumberOfReviews(unit.getReviews().size());
        dto.setAverageRating(calculateAverageRating(unit.getReviews()));
        dto.setAverageWorkload(calculateAverageWorkload(unit.getReviews()));
        dto.setAverageFinalGrade(calculateAverageFinalGrade(unit.getReviews()));
        dto.setWouldTakeAgainRatio(calculateWouldTakeAgainRatio(unit.getReviews()));

        // Unit reviews in ReviewDTO format.
        dto.setReviews(ReviewMapper.mapToDTOs(unit.getReviews()));
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

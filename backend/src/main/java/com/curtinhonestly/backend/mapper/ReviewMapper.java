package com.curtinhonestly.backend.mapper;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.dto.ReviewDTO;

import java.util.ArrayList;
import java.util.List;

public class ReviewMapper {

    public static ReviewDTO mapToDTO(Review review) {
        ReviewDTO dto = new ReviewDTO();

        // Review details
        dto.setRating(review.getRating());
        dto.setFinalGrade(review.getFinalGrade());
        dto.setReviewText(review.getReviewText());
        dto.setSemesterTaken(review.getSemesterTaken());
        dto.setProfessor(review.getProfessor());
        dto.setWorkload(review.getWorkload());
        dto.setHasExam(review.isHasExam());
        dto.setWouldTakeAgain(review.isWouldTakeAgain());

        if (review.getUser() != null) {
            dto.setReviewerVerified(review.getUser().isVerifiedStudent());
        }

        return dto;
    }

    public static List<ReviewDTO> mapToDTOs(List<Review> reviews) {

        List<ReviewDTO> dtos = new ArrayList<>();
        for(Review review :reviews) {
            dtos.add(mapToDTO(review));
        }
        return dtos;
    }
}

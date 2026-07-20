package com.curtinhonestly.backend.mapper;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.dto.MyReviewDTO;
import com.curtinhonestly.backend.dto.ReviewDTO;

import java.util.ArrayList;
import java.util.List;

public class ReviewMapper {

    public static ReviewDTO mapToDTO(Review review) {
        ReviewDTO dto = new ReviewDTO();

        dto.setId(review.getId());
        dto.setRating(review.getRating());
        dto.setFinalGrade(review.getFinalGrade());
        dto.setReviewText(review.getReviewText());
        dto.setSemesterTaken(review.getSemesterTaken());
        dto.setProfessor(review.getProfessor());
        dto.setWorkload(review.getWorkload());
        dto.setHasExam(review.isHasExam());
        dto.setWouldTakeAgain(review.isWouldTakeAgain());
        dto.setLikeCount(review.getLikeCount());
        dto.setLikedByCurrentUser(false);
        dto.setCreatedAt(review.getCreatedAt());

        if (review.getUser() != null) {
            dto.setReviewerVerified(review.getUser().isVerifiedStudent());
        }

        return dto;
    }

    public static MyReviewDTO mapToMyReviewDTO(Review review) {
        String unitCode = review.getUnit() != null ? review.getUnit().getCode() : "";
        String unitName = review.getUnit() != null ? review.getUnit().getName() : "";
        return new MyReviewDTO(
                review.getId(),
                unitCode,
                unitName,
                review.getRating(),
                review.getFinalGrade(),
                review.getReviewText(),
                review.getSemesterTaken(),
                review.getProfessor(),
                review.getWorkload(),
                review.isHasExam(),
                review.isWouldTakeAgain(),
                review.getLikeCount(),
                review.getCreatedAt()
        );
    }

    public static List<MyReviewDTO> mapToMyReviewDTOs(List<Review> reviews) {
        return reviews.stream().map(ReviewMapper::mapToMyReviewDTO).toList();
    }

    public static List<ReviewDTO> mapToDTOs(List<Review> reviews) {

        List<ReviewDTO> dtos = new ArrayList<>();
        for(Review review :reviews) {
            dtos.add(mapToDTO(review));
        }
        return dtos;
    }
}

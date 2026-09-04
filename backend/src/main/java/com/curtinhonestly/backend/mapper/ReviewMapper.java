package com.curtinhonestly.backend.mapper;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.dto.MyReviewDTO;
import com.curtinhonestly.backend.dto.ReviewDTO;
import com.curtinhonestly.backend.util.ReviewerRank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReviewMapper {

    public static ReviewDTO mapToDTO(Review review) {
        return mapToDTO(review, Map.of());
    }

    /**
     * Maps a review, stamping the author's standing from {@code ranksByUserId}
     * when present. The map is looked up here and never copied onto the DTO, so
     * the card carries the tier and nothing that identifies the author.
     */
    public static ReviewDTO mapToDTO(Review review, Map<String, ReviewerRank> ranksByUserId) {
        ReviewDTO dto = new ReviewDTO();

        dto.setId(review.getId());
        dto.setRating(review.getRating());
        dto.setFinalGrade(review.getFinalGrade());
        dto.setReviewText(review.getReviewText());
        dto.setTermType(review.getTermType());
        dto.setTermYear(review.getTermYear());
        dto.setProfessor(review.getProfessor());
        dto.setWorkload(review.getWorkload());
        dto.setHasExam(review.isHasExam());
        dto.setWouldTakeAgain(review.isWouldTakeAgain());
        dto.setTags(review.getTags());
        dto.setLikeCount(review.getLikeCount());
        dto.setLikedByCurrentUser(false);
        dto.setCreatedAt(review.getCreatedAt());

        if (review.getUser() != null) {
            dto.setReviewerVerified(review.getUser().isVerifiedStudent());
            if (ranksByUserId != null && review.getUser().getId() != null) {
                applyReviewerRank(dto, ranksByUserId.get(review.getUser().getId()));
            }
        }

        return dto;
    }

    /**
     * Anonymised reviews and authors with no activity get nothing: a card with
     * no tier is the normal case, not an error state.
     */
    public static void applyReviewerRank(ReviewDTO dto, ReviewerRank rank) {
        if (rank == null || !rank.hasActivity()) {
            return;
        }
        dto.setReviewerTier(rank.activityTier());
        dto.setReviewerTierLabel(rank.activityTier().getLabel());
        if (rank.recognitionTier() != null) {
            dto.setReviewerRecognition(rank.recognitionTier());
            dto.setReviewerRecognitionLabel(rank.recognitionTier().getLabel());
        }
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
                review.getTermType(),
                review.getTermYear(),
                review.getProfessor(),
                review.getWorkload(),
                review.isHasExam(),
                review.isWouldTakeAgain(),
                review.getTags(),
                review.getLikeCount(),
                review.getCreatedAt()
        );
    }

    public static List<MyReviewDTO> mapToMyReviewDTOs(List<Review> reviews) {
        return reviews.stream().map(ReviewMapper::mapToMyReviewDTO).toList();
    }

    public static List<ReviewDTO> mapToDTOs(List<Review> reviews) {
        return mapToDTOs(reviews, Map.of());
    }

    public static List<ReviewDTO> mapToDTOs(List<Review> reviews, Map<String, ReviewerRank> ranksByUserId) {
        List<ReviewDTO> dtos = new ArrayList<>();
        for (Review review : reviews) {
            dtos.add(mapToDTO(review, ranksByUserId));
        }
        return dtos;
    }
}

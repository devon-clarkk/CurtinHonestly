package com.curtinhonestly.backend.dto;

import com.curtinhonestly.backend.domain.RecognitionTier;
import com.curtinhonestly.backend.domain.ReviewerTier;
import com.curtinhonestly.backend.util.ReviewerRank;

/**
 * The signed-in student's own reviewer standing. Carries only counts and tier
 * names: no ids, no email, nothing about anyone else.
 *
 * Nullable fields: {@code recognitionTier} and its label until the first
 * recognition threshold is reached; {@code nextTierLabel} and
 * {@code nextRecognitionLabel} once the top of the respective ladder is reached.
 */
public record ReviewerProfileDTO(
        ReviewerTier activityTier,
        String activityTierLabel,
        RecognitionTier recognitionTier,
        String recognitionTierLabel,
        long reviewCount,
        long likesReceived,
        int reviewsToNextTier,
        String nextTierLabel,
        int nextTierThreshold,
        int likesToNextRecognition,
        String nextRecognitionLabel,
        int nextRecognitionThreshold
) {

    public static ReviewerProfileDTO from(ReviewerRank rank) {
        ReviewerTier nextTier = rank.nextTier();
        RecognitionTier nextRecognition = rank.nextRecognition();
        return new ReviewerProfileDTO(
                rank.activityTier(),
                rank.activityTier().getLabel(),
                rank.recognitionTier(),
                rank.recognitionTier() == null ? null : rank.recognitionTier().getLabel(),
                rank.reviewCount(),
                rank.likesReceived(),
                rank.reviewsToNextTier(),
                nextTier == null ? null : nextTier.getLabel(),
                nextTier == null ? 0 : nextTier.getMinReviews(),
                rank.likesToNextRecognition(),
                nextRecognition == null ? null : nextRecognition.getLabel(),
                nextRecognition == null ? 0 : nextRecognition.getMinLikes()
        );
    }
}

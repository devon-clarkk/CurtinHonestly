package com.curtinhonestly.backend.util;

import com.curtinhonestly.backend.domain.RecognitionTier;
import com.curtinhonestly.backend.domain.ReviewerTier;

/**
 * A reviewer's standing, computed from two numbers: how many reviews they have
 * written and how many helpful marks those reviews have collected in total.
 *
 * Pure: no Spring, no database, so it is unit-tested directly and can be built
 * from a single aggregate row (see {@code ReviewerRankService}). The thresholds
 * themselves live on {@link ReviewerTier} and {@link RecognitionTier}; this
 * class only reads them.
 *
 * @param activityTier           tier earned by review count, never null
 * @param recognitionTier        tier earned by likes received, null until the first threshold
 * @param reviewCount            reviews written, never negative
 * @param likesReceived          helpful marks across all of the reviewer's reviews, never negative
 * @param reviewsToNextTier      reviews still needed for the next activity tier, 0 at the top
 * @param likesToNextRecognition likes still needed for the next recognition tier, 0 at the top
 */
public record ReviewerRank(
        ReviewerTier activityTier,
        RecognitionTier recognitionTier,
        long reviewCount,
        long likesReceived,
        int reviewsToNextTier,
        int likesToNextRecognition
) {

    public static final ReviewerRank NONE = of(0, 0);

    public static ReviewerRank of(long reviewCount, long likesReceived) {
        long reviews = Math.max(0, reviewCount);
        long likes = Math.max(0, likesReceived);

        ReviewerTier activity = ReviewerTier.forReviewCount(reviews);
        ReviewerTier nextActivity = activity.next();
        int reviewsToNext = nextActivity == null ? 0 : clampToInt(nextActivity.getMinReviews() - reviews);

        RecognitionTier recognition = RecognitionTier.forLikesReceived(likes);
        RecognitionTier nextRecognition = RecognitionTier.after(recognition);
        int likesToNext = nextRecognition == null ? 0 : clampToInt(nextRecognition.getMinLikes() - likes);

        return new ReviewerRank(activity, recognition, reviews, likes, reviewsToNext, likesToNext);
    }

    /** The activity tier this reviewer is working towards, or null at the top. */
    public ReviewerTier nextTier() {
        return activityTier.next();
    }

    /** The recognition tier this reviewer is working towards, or null at the top. */
    public RecognitionTier nextRecognition() {
        return RecognitionTier.after(recognitionTier);
    }

    /** True once the reviewer has written at least one review. */
    public boolean hasActivity() {
        return activityTier != ReviewerTier.LURKER;
    }

    private static int clampToInt(long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }
}

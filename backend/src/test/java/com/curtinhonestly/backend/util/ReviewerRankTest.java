package com.curtinhonestly.backend.util;

import com.curtinhonestly.backend.domain.RecognitionTier;
import com.curtinhonestly.backend.domain.ReviewerTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test, no Spring context. Pins the tier boundaries so a threshold
 * change is a deliberate edit to the enum and to this file, not a surprise.
 */
class ReviewerRankTest {

    @ParameterizedTest
    @CsvSource({
            "0, LURKER",
            "1, NEWCOMER",
            "2, NEWCOMER",
            "3, CONTRIBUTOR",
            "5, CONTRIBUTOR",
            "6, REGULAR",
            "9, REGULAR",
            "10, TOP_REVIEWER",
            "19, TOP_REVIEWER",
            "20, LEGEND",
            "1000, LEGEND",
    })
    void activityTierFollowsReviewCount(long reviews, ReviewerTier expected) {
        assertEquals(expected, ReviewerRank.of(reviews, 0).activityTier());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1",
            "1, 2",
            "2, 1",
            "3, 3",
            "7, 3",
            "10, 10",
            "19, 1",
            "20, 0",
            "50, 0",
    })
    void reviewsToNextTierCountsUpToTheNextThreshold(long reviews, int expected) {
        assertEquals(expected, ReviewerRank.of(reviews, 0).reviewsToNextTier());
    }

    @ParameterizedTest
    @CsvSource({
            "0, ",
            "4, ",
            "5, APPRECIATED",
            "14, APPRECIATED",
            "15, VALUED_REVIEWER",
            "49, VALUED_REVIEWER",
            "50, COMMUNITY_FAVOURITE",
            "9999, COMMUNITY_FAVOURITE",
    })
    void recognitionFollowsLikesReceivedAndIsNullBelowTheFirstThreshold(long likes, RecognitionTier expected) {
        assertEquals(expected, ReviewerRank.of(0, likes).recognitionTier());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5",
            "4, 1",
            "5, 10",
            "14, 1",
            "15, 35",
            "49, 1",
            "50, 0",
    })
    void likesToNextRecognitionCountsUpToTheNextThreshold(long likes, int expected) {
        assertEquals(expected, ReviewerRank.of(0, likes).likesToNextRecognition());
    }

    @Test
    void theTwoAxesAreIndependent() {
        ReviewerRank oneViralReview = ReviewerRank.of(1, 60);
        assertEquals(ReviewerTier.NEWCOMER, oneViralReview.activityTier());
        assertEquals(RecognitionTier.COMMUNITY_FAVOURITE, oneViralReview.recognitionTier());

        ReviewerRank prolificButUnliked = ReviewerRank.of(25, 0);
        assertEquals(ReviewerTier.LEGEND, prolificButUnliked.activityTier());
        assertNull(prolificButUnliked.recognitionTier());
    }

    @Test
    void nextTierAndNextRecognitionAreExposedAndNullAtTheTop() {
        ReviewerRank starting = ReviewerRank.of(0, 0);
        assertEquals(ReviewerTier.NEWCOMER, starting.nextTier());
        assertEquals(RecognitionTier.APPRECIATED, starting.nextRecognition());

        ReviewerRank midway = ReviewerRank.of(7, 20);
        assertEquals(ReviewerTier.TOP_REVIEWER, midway.nextTier());
        assertEquals(RecognitionTier.COMMUNITY_FAVOURITE, midway.nextRecognition());

        ReviewerRank top = ReviewerRank.of(20, 50);
        assertNull(top.nextTier());
        assertNull(top.nextRecognition());
    }

    @Test
    void negativeInputsAreTreatedAsZero() {
        ReviewerRank rank = ReviewerRank.of(-3, -10);
        assertEquals(0, rank.reviewCount());
        assertEquals(0, rank.likesReceived());
        assertEquals(ReviewerTier.LURKER, rank.activityTier());
        assertNull(rank.recognitionTier());
        assertEquals(1, rank.reviewsToNextTier());
        assertEquals(5, rank.likesToNextRecognition());
    }

    @Test
    void hasActivityIsFalseOnlyForLurkers() {
        assertFalse(ReviewerRank.of(0, 100).hasActivity());
        assertTrue(ReviewerRank.of(1, 0).hasActivity());
        assertFalse(ReviewerRank.NONE.hasActivity());
    }

    @Test
    void tierLabelsAreHumanReadable() {
        assertEquals("Top Reviewer", ReviewerTier.TOP_REVIEWER.getLabel());
        assertEquals("Community Favourite", RecognitionTier.COMMUNITY_FAVOURITE.getLabel());
    }

    @Test
    void tierThresholdsAreStrictlyIncreasing() {
        ReviewerTier[] tiers = ReviewerTier.values();
        for (int i = 1; i < tiers.length; i++) {
            assertTrue(tiers[i].getMinReviews() > tiers[i - 1].getMinReviews(), tiers[i].name());
        }
        RecognitionTier[] recognitions = RecognitionTier.values();
        for (int i = 1; i < recognitions.length; i++) {
            assertTrue(recognitions[i].getMinLikes() > recognitions[i - 1].getMinLikes(), recognitions[i].name());
        }
        assertEquals(0, ReviewerTier.LURKER.getMinReviews());
    }
}

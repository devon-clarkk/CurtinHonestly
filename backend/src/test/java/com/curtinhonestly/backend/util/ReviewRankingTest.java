package com.curtinhonestly.backend.util;

import com.curtinhonestly.backend.domain.Review;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test - no Spring context, so it runs in milliseconds. The
 * integration side (that the endpoints actually apply this) is covered by
 * ReviewOrderingTest.
 */
class ReviewRankingTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    private static Review review(String id, int ageInDays, int likes) {
        Review review = new Review();
        review.setId(id);
        review.setLikeCount(likes);
        review.setCreatedAt(NOW.minus(ageInDays, ChronoUnit.DAYS));
        return review;
    }

    @Test
    void withNoLikesItIsPlainNewestFirst() {
        List<Review> ranked = ReviewRanking.rank(
                List.of(review("old", 400, 0), review("newest", 1, 0), review("middle", 90, 0)), NOW);

        assertEquals(List.of("newest", "middle", "old"), ranked.stream().map(Review::getId).toList());
    }

    @Test
    void likesPullAnOlderReviewAboveANewerUnlikedOne() {
        // 7 likes == log2(8) == 3 doublings == 90 days of freshness, so the
        // 120-day-old review out-scores the unliked 60-day-old one.
        List<Review> ranked = ReviewRanking.rank(
                List.of(review("newer-unliked", 60, 0), review("older-liked", 120, 7)), NOW);

        assertEquals(List.of("older-liked", "newer-unliked"), ranked.stream().map(Review::getId).toList());
    }

    @Test
    void likesCannotRescueAReviewFromYearsAgo() {
        // Deliberate: a stale review of a unit that has since changed hands
        // should not outrank last month's, however many likes it collected.
        List<Review> ranked = ReviewRanking.rank(
                List.of(review("ancient-popular", 1095, 500), review("recent-unliked", 30, 0)), NOW);

        assertEquals(List.of("recent-unliked", "ancient-popular"), ranked.stream().map(Review::getId).toList());
    }

    @Test
    void sameAgeIsDecidedByLikes() {
        List<Review> ranked = ReviewRanking.rank(
                List.of(review("few", 30, 1), review("many", 30, 20), review("none", 30, 0)), NOW);

        assertEquals(List.of("many", "few", "none"), ranked.stream().map(Review::getId).toList());
    }

    @Test
    void identicalScoresStillOrderDeterministically() {
        List<Review> first = ReviewRanking.rank(List.of(review("b", 30, 2), review("a", 30, 2)), NOW);
        List<Review> second = ReviewRanking.rank(List.of(review("a", 30, 2), review("b", 30, 2)), NOW);

        assertEquals(first.stream().map(Review::getId).toList(), second.stream().map(Review::getId).toList());
    }

    @Test
    void oneDoublingOfLikesIsWorthTheAdvertisedNumberOfDays() {
        // The knob in the javadoc has to mean what it says: 1 like (one
        // doubling from zero) buys exactly LIKE_BOOST_DAYS of freshness.
        double liked = ReviewRanking.score(review("liked", 30, 1), NOW);
        double unliked = ReviewRanking.score(review("unliked", 30 - (int) ReviewRanking.LIKE_BOOST_DAYS, 0), NOW);

        assertEquals(unliked, liked, 0.001);
    }

    @Test
    void emptyAndNullInputAreSafe() {
        assertTrue(ReviewRanking.rank(null, NOW).isEmpty());
        assertTrue(ReviewRanking.rank(List.of(), NOW).isEmpty());
    }
}

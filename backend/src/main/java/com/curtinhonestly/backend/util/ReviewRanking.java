package com.curtinhonestly.backend.util;

import com.curtinhonestly.backend.domain.Review;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The order reviews appear in on a unit page: newest first, with well-liked
 * reviews pulled up.
 *
 * This is the single authority on review ordering. Neither the JPA collection
 * nor the repository query is ordered for display - {@code Unit.reviews} has no
 * {@code @OrderBy} on purpose, because an entity-level ordering that ranking
 * then overwrites is just a second answer to the same question for a future
 * reader to trip over. Anything that renders a list of reviews goes through
 * {@link #rank(Collection)}.
 *
 * <p>The score is:
 *
 * <pre>score = -ageInDays + LIKE_BOOST_DAYS * log2(1 + likeCount)</pre>
 *
 * Read it as "each doubling of likes is worth {@value #LIKE_BOOST_DAYS} days of
 * freshness": 1 like ranks a review as if it were a month newer, 3 likes two
 * months, 7 likes three months. A review nobody has liked contributes nothing
 * from the second term, so an unliked list degrades to plain newest-first -
 * which is the normal case and the behaviour that was asked for.
 *
 * <p>Age is linear rather than decayed, so recency dominates over long spans on
 * purpose: a three-year-old review of a unit that has since changed coordinator
 * should not outrank last semester's no matter how many likes it collected.
 * The practical effect is that likes reorder reviews within roughly a year of
 * each other. Widen that window by raising {@link #LIKE_BOOST_DAYS} - it is the
 * one knob here.
 *
 * <p>Same spirit as {@code Unit.relevanceScore}, which trades review count off
 * against staleness to order the catalog.
 */
public final class ReviewRanking {

    /** How much freshness one doubling of likes is worth. See class javadoc. */
    public static final double LIKE_BOOST_DAYS = 30.0;

    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final double LOG_2 = Math.log(2);

    private ReviewRanking() {
    }

    /**
     * Highest score first. Ties break on newest, then on id, so pagination and
     * repeat requests are stable rather than left to whatever order the rows
     * came back in.
     */
    public static Comparator<Review> comparator(Instant now) {
        return Comparator.comparingDouble((Review review) -> score(review, now))
                .reversed()
                .thenComparing(Review::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Review::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    public static double score(Review review, Instant now) {
        return -ageInDays(review, now) + LIKE_BOOST_DAYS * log2(1 + Math.max(0, review.getLikeCount()));
    }

    /** Returns a new ranked list; the input collection is not modified. */
    public static List<Review> rank(Collection<Review> reviews) {
        return rank(reviews, Instant.now());
    }

    public static List<Review> rank(Collection<Review> reviews, Instant now) {
        if (reviews == null || reviews.isEmpty()) {
            return new ArrayList<>();
        }
        List<Review> ranked = new ArrayList<>(reviews);
        ranked.sort(comparator(now));
        return ranked;
    }

    /**
     * A review with no timestamp is treated as brand new rather than infinitely
     * old. createdAt is NOT NULL in the schema, so this only guards against a
     * detached or hand-built entity in a test.
     */
    private static double ageInDays(Review review, Instant now) {
        if (review.getCreatedAt() == null) {
            return 0;
        }
        Duration age = Duration.between(review.getCreatedAt(), now);
        return (age.getSeconds() + age.getNano() / 1_000_000_000.0) / SECONDS_PER_DAY;
    }

    private static double log2(double value) {
        return Math.log(value) / LOG_2;
    }
}

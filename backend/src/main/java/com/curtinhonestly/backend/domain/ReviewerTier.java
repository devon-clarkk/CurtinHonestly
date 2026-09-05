package com.curtinhonestly.backend.domain;

import lombok.Getter;

/**
 * Activity tier: how much a student has written. Ordered from least to most
 * active, and the thresholds live here and nowhere else. Each constant names
 * the minimum review count it needs, so the tier for a count is the last
 * constant whose minimum is not above it. Ranking itself is done by
 * {@code ReviewerRank.of}, which is the one caller that should read these.
 */
@Getter
public enum ReviewerTier {
    LURKER(0, "Lurker"),
    NEWCOMER(1, "Newcomer"),
    CONTRIBUTOR(3, "Contributor"),
    REGULAR(6, "Regular"),
    TOP_REVIEWER(10, "Top Reviewer"),
    LEGEND(20, "Legend");

    private final int minReviews;
    private final String label;

    ReviewerTier(int minReviews, String label) {
        this.minReviews = minReviews;
        this.label = label;
    }

    /** The tier a student with this many reviews holds. Negative counts read as zero. */
    public static ReviewerTier forReviewCount(long reviewCount) {
        ReviewerTier tier = LURKER;
        for (ReviewerTier candidate : values()) {
            if (reviewCount >= candidate.minReviews) {
                tier = candidate;
            }
        }
        return tier;
    }

    /** The tier after this one, or null at the top. */
    public ReviewerTier next() {
        int index = ordinal() + 1;
        return index < values().length ? values()[index] : null;
    }
}

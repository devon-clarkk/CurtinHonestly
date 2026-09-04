package com.curtinhonestly.backend.domain;

import lombok.Getter;

/**
 * Recognition tier: how much other students have valued a reviewer's work,
 * measured as the total helpful marks across every review they have written.
 * Separate from {@link ReviewerTier} on purpose, a single well-liked review
 * earns this without any volume. There is no bottom constant: a reviewer
 * below the first threshold simply has no recognition yet, which callers see
 * as null. Thresholds live here and nowhere else.
 */
@Getter
public enum RecognitionTier {
    APPRECIATED(5, "Appreciated"),
    VALUED_REVIEWER(15, "Valued Reviewer"),
    COMMUNITY_FAVOURITE(50, "Community Favourite");

    private final int minLikes;
    private final String label;

    RecognitionTier(int minLikes, String label) {
        this.minLikes = minLikes;
        this.label = label;
    }

    /** The recognition earned by this many likes received, or null below the first threshold. */
    public static RecognitionTier forLikesReceived(long likesReceived) {
        RecognitionTier tier = null;
        for (RecognitionTier candidate : values()) {
            if (likesReceived >= candidate.minLikes) {
                tier = candidate;
            }
        }
        return tier;
    }

    /**
     * The recognition after the given one, or null once the top is reached.
     * Passing null returns the first tier, so the same call answers "what is
     * next" for a reviewer with no recognition yet.
     */
    public static RecognitionTier after(RecognitionTier current) {
        if (current == null) {
            return values()[0];
        }
        int index = current.ordinal() + 1;
        return index < values().length ? values()[index] : null;
    }
}

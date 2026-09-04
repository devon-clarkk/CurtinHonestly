package com.curtinhonestly.backend.service.recommendation;

/**
 * Every tunable number in the recommender, in one place. The algorithm classes
 * reference these by name so a weight can be changed without reading the maths.
 *
 * <p>The values marked "tuned" were chosen with the offline harness in
 * RecommendationEvaluationTest, which plants known student types in a synthetic
 * population and measures leave-one-out hit rate and confidence calibration.
 */
public final class RecommendationWeights {

    // Affinity: how one review becomes a score in [-1, 1]. The star rating maps
    // linearly (1..5 -> -1..1) and the other signals nudge it before clamping.
    public static final double WOULD_TAKE_AGAIN_BONUS = 0.3;
    public static final double WOULD_NOT_TAKE_AGAIN_PENALTY = -0.3;
    public static final int HIGH_GRADE_MIN = 75;
    public static final double HIGH_GRADE_BONUS = 0.2;
    public static final int GOOD_GRADE_MIN = 65;
    public static final double GOOD_GRADE_BONUS = 0.1;
    public static final int FAIL_GRADE_MAX_EXCLUSIVE = 50;
    public static final double FAIL_GRADE_PENALTY = -0.2;

    // A unit counts as liked or disliked (for profiles and reasons) past these.
    public static final double LIKED_AFFINITY = 0.25;
    public static final double DISLIKED_AFFINITY = -0.25;

    // A unit a student marked as completed but did not review is a weak positive
    // in their taste vector: they chose it. Used for similarity only; completed
    // units are never recommended back. Tuned.
    public static final double COMPLETED_UNIT_AFFINITY = 0.15;

    // User-to-user similarity.
    public static final int MIN_OVERLAP = 1;
    // Significance shrink: sim * overlap / (overlap + OVERLAP_SHRINK), so one
    // shared unit cannot make two students twins. Tuned.
    public static final double OVERLAP_SHRINK = 2.0;
    public static final double COLLAB_WEIGHT = 0.85;
    public static final double PROFILE_WEIGHT = 0.15;
    public static final double WORKLOAD_SCALE = 10.0;

    // Neighbourhood. Tuned.
    public static final int NEIGHBOUR_LIMIT = 25;
    public static final double NEIGHBOUR_MIN_SIMILARITY = 0.1;
    public static final int MIN_REVIEWS_FOR_PERSONALISED = 2;

    // Output lists. Tuned.
    public static final double RECOMMEND_THRESHOLD = 0.35;
    public static final double AVOID_THRESHOLD = -0.35;
    public static final int LIST_LIMIT = 12;
    public static final int MAX_REASONS = 3;
    public static final int CONFIDENCE_MIN = 5;
    public static final int CONFIDENCE_MAX = 99;
    // confidence = 100 * (1 - e^(-supportMass / CONFIDENCE_MASS_SCALE)) * agreement,
    // where supportMass is the summed similarity of the neighbours who reviewed
    // the unit. A larger scale needs more support for the same confidence. Tuned.
    public static final double CONFIDENCE_MASS_SCALE = 2.5;
    // Order of the recommended list: confidence * ((predicted + 1) / 2) ^ weight.
    // 0 orders by confidence alone. Tuned.
    public static final double RANK_AFFINITY_WEIGHT = 0.0;

    // Cold start: Bayesian-smoothed rating (v/(v+m))*R + (m/(v+m))*C.
    public static final double BAYESIAN_PRIOR_WEIGHT = 3.0;
    public static final int FALLBACK_CONFIDENCE_BASE = 20;
    public static final int FALLBACK_CONFIDENCE_PER_REVIEW = 5;
    public static final int FALLBACK_CONFIDENCE_MAX = 60;

    // Unit-to-unit similarity.
    public static final int SIMILAR_UNITS_LIMIT = 8;
    public static final int SIMILAR_MIN_CO_REVIEWERS = 2;
    // Same shrink idea as OVERLAP_SHRINK, over the shared reviewers of two units.
    public static final double ITEM_OVERLAP_SHRINK = 2.0;
    // Below this many co-review matches the list is topped up with same
    // faculty and level units of the closest average rating.
    public static final int SIMILAR_MIN_CO_REVIEW_ITEMS = 3;
    public static final double RATING_RANGE = 4.0;

    // Reason generation.
    public static final double WORKLOAD_SIMILAR_TOLERANCE = 1.5;
    public static final double WORKLOAD_DIFFERENT_GAP = 3.0;
    public static final double TAG_PREFERENCE_SHARE = 0.5;
    public static final int MAX_ANCHOR_UNITS_IN_REASON = 2;

    private RecommendationWeights() {
    }
}

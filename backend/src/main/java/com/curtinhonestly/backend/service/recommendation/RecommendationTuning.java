package com.curtinhonestly.backend.service.recommendation;

import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.AVOID_THRESHOLD;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.COLLAB_WEIGHT;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.COMPLETED_UNIT_AFFINITY;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.CONFIDENCE_MASS_SCALE;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.NEIGHBOUR_LIMIT;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.NEIGHBOUR_MIN_SIMILARITY;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.OVERLAP_SHRINK;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.PROFILE_WEIGHT;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.RANK_AFFINITY_WEIGHT;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.RECOMMEND_THRESHOLD;

/**
 * The subset of {@link RecommendationWeights} that the offline evaluation
 * harness sweeps. Production always runs {@link #defaults()}, which reads the
 * constants, so a value tuned in the harness is shipped by editing the constant.
 *
 * @param overlapShrink          user similarity shrink: sim * overlap / (overlap + shrink)
 * @param collabWeight           weight of the shared-unit cosine in the blend
 * @param profileWeight          weight of the workload and tag profile term
 * @param neighbourLimit         most similar students consulted per target
 * @param neighbourMinSimilarity a student below this similarity is not a neighbour
 * @param recommendThreshold     predicted affinity needed to be recommended
 * @param avoidThreshold         predicted affinity at or below which a unit is advised against
 * @param confidenceMassScale    confidence = 100 * (1 - e^(-mass / scale)) * agreement
 * @param rankAffinityWeight     0 ranks the recommended list by confidence alone; higher values
 *                               weight the predicted affinity into the order
 * @param completedUnitAffinity  affinity given to a unit a student completed but did not review,
 *                               for similarity only; 0 turns the signal off
 */
public record RecommendationTuning(
        double overlapShrink,
        double collabWeight,
        double profileWeight,
        int neighbourLimit,
        double neighbourMinSimilarity,
        double recommendThreshold,
        double avoidThreshold,
        double confidenceMassScale,
        double rankAffinityWeight,
        double completedUnitAffinity
) {

    private static final RecommendationTuning DEFAULTS = new RecommendationTuning(
            OVERLAP_SHRINK, COLLAB_WEIGHT, PROFILE_WEIGHT, NEIGHBOUR_LIMIT, NEIGHBOUR_MIN_SIMILARITY,
            RECOMMEND_THRESHOLD, AVOID_THRESHOLD, CONFIDENCE_MASS_SCALE, RANK_AFFINITY_WEIGHT,
            COMPLETED_UNIT_AFFINITY);

    public static RecommendationTuning defaults() {
        return DEFAULTS;
    }

    public RecommendationTuning withOverlapShrink(double value) {
        return new RecommendationTuning(value, collabWeight, profileWeight, neighbourLimit, neighbourMinSimilarity,
                recommendThreshold, avoidThreshold, confidenceMassScale, rankAffinityWeight, completedUnitAffinity);
    }

    public RecommendationTuning withProfileWeight(double value) {
        return new RecommendationTuning(overlapShrink, 1 - value, value, neighbourLimit, neighbourMinSimilarity,
                recommendThreshold, avoidThreshold, confidenceMassScale, rankAffinityWeight, completedUnitAffinity);
    }

    public RecommendationTuning withNeighbourLimit(int value) {
        return new RecommendationTuning(overlapShrink, collabWeight, profileWeight, value, neighbourMinSimilarity,
                recommendThreshold, avoidThreshold, confidenceMassScale, rankAffinityWeight, completedUnitAffinity);
    }

    public RecommendationTuning withNeighbourMinSimilarity(double value) {
        return new RecommendationTuning(overlapShrink, collabWeight, profileWeight, neighbourLimit, value,
                recommendThreshold, avoidThreshold, confidenceMassScale, rankAffinityWeight, completedUnitAffinity);
    }

    public RecommendationTuning withRecommendThreshold(double value) {
        return new RecommendationTuning(overlapShrink, collabWeight, profileWeight, neighbourLimit, neighbourMinSimilarity,
                value, -value, confidenceMassScale, rankAffinityWeight, completedUnitAffinity);
    }

    public RecommendationTuning withConfidenceMassScale(double value) {
        return new RecommendationTuning(overlapShrink, collabWeight, profileWeight, neighbourLimit, neighbourMinSimilarity,
                recommendThreshold, avoidThreshold, value, rankAffinityWeight, completedUnitAffinity);
    }

    public RecommendationTuning withRankAffinityWeight(double value) {
        return new RecommendationTuning(overlapShrink, collabWeight, profileWeight, neighbourLimit, neighbourMinSimilarity,
                recommendThreshold, avoidThreshold, confidenceMassScale, value, completedUnitAffinity);
    }

    public RecommendationTuning withCompletedUnitAffinity(double value) {
        return new RecommendationTuning(overlapShrink, collabWeight, profileWeight, neighbourLimit, neighbourMinSimilarity,
                recommendThreshold, avoidThreshold, confidenceMassScale, rankAffinityWeight, value);
    }
}

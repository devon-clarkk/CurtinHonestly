package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.ReviewTag;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One student's taste, derived from their reviews plus the units they marked as
 * completed without reviewing.
 *
 * @param affinities        unitCode -> affinity in [-1, 1], reviewed units only
 * @param vector            affinities plus every completed-but-unreviewed unit at
 *                          the weak completed-unit weight; this is what similarity
 *                          compares. Identical to affinities when the student has
 *                          recorded no extra completed units or the weight is 0.
 * @param likedWorkloadMean mean workload (0-10) of the units they liked, null when none
 * @param likedTagShares    share of liked units carrying each tag (empty when nothing liked)
 * @param facultyMix        share of reviewed units per faculty
 */
public record TasteProfile(
        String userId,
        Map<String, Double> affinities,
        Map<String, Double> vector,
        Double likedWorkloadMean,
        Map<ReviewTag, Double> likedTagShares,
        Map<Faculty, Double> facultyMix
) {
    public TasteProfile {
        affinities = Map.copyOf(affinities);
        vector = Map.copyOf(vector);
        likedTagShares = Map.copyOf(likedTagShares);
        facultyMix = Map.copyOf(facultyMix);
    }

    /** Number of reviews the profile rests on. Completed units do not count. */
    public int reviewCount() {
        return affinities.size();
    }

    public double affinityOf(String unitCode) {
        return affinities.getOrDefault(unitCode, 0.0);
    }

    public boolean hasReviewed(String unitCode) {
        return affinities.containsKey(unitCode);
    }

    /** Units the student marked as completed but has not reviewed. */
    public Set<String> completedUnits() {
        return vector.keySet().stream()
                .filter(code -> !affinities.containsKey(code))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** True when the student reviewed the unit, or completed it without reviewing. */
    public boolean took(String unitCode) {
        return vector.containsKey(unitCode);
    }

    public Set<String> likedUnits() {
        return affinities.entrySet().stream()
                .filter(e -> e.getValue() >= RecommendationWeights.LIKED_AFFINITY)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> dislikedUnits() {
        return affinities.entrySet().stream()
                .filter(e -> e.getValue() <= RecommendationWeights.DISLIKED_AFFINITY)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}

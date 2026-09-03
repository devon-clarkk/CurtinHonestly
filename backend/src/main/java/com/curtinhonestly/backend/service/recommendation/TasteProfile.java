package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.ReviewTag;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One student's taste, derived purely from their reviews.
 *
 * @param affinities        unitCode -> affinity in [-1, 1]
 * @param likedWorkloadMean mean workload (0-10) of the units they liked, null when none
 * @param likedTagShares    share of liked units carrying each tag (empty when nothing liked)
 * @param facultyMix        share of reviewed units per faculty
 */
public record TasteProfile(
        String userId,
        Map<String, Double> affinities,
        Double likedWorkloadMean,
        Map<ReviewTag, Double> likedTagShares,
        Map<Faculty, Double> facultyMix
) {
    public TasteProfile {
        affinities = Map.copyOf(affinities);
        likedTagShares = Map.copyOf(likedTagShares);
        facultyMix = Map.copyOf(facultyMix);
    }

    public int reviewCount() {
        return affinities.size();
    }

    public double affinityOf(String unitCode) {
        return affinities.getOrDefault(unitCode, 0.0);
    }

    public boolean hasReviewed(String unitCode) {
        return affinities.containsKey(unitCode);
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

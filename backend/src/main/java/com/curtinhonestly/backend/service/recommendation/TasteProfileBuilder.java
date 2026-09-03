package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.ReviewTag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.FAIL_GRADE_MAX_EXCLUSIVE;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.FAIL_GRADE_PENALTY;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.GOOD_GRADE_BONUS;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.GOOD_GRADE_MIN;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.HIGH_GRADE_BONUS;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.HIGH_GRADE_MIN;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.LIKED_AFFINITY;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.WOULD_NOT_TAKE_AGAIN_PENALTY;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.WOULD_TAKE_AGAIN_BONUS;

/** Pure functions that turn review observations into {@link TasteProfile}s. */
public final class TasteProfileBuilder {

    private TasteProfileBuilder() {
    }

    /**
     * Affinity of one review in [-1, 1]: rating 1..5 maps linearly to -1..1,
     * would-take-again and the final grade (when given) nudge it, then clamp.
     */
    public static double affinity(int rating, boolean wouldTakeAgain, Integer finalGrade) {
        double score = (rating - 3) / 2.0;
        score += wouldTakeAgain ? WOULD_TAKE_AGAIN_BONUS : WOULD_NOT_TAKE_AGAIN_PENALTY;
        if (finalGrade != null) {
            if (finalGrade >= HIGH_GRADE_MIN) {
                score += HIGH_GRADE_BONUS;
            } else if (finalGrade >= GOOD_GRADE_MIN) {
                score += GOOD_GRADE_BONUS;
            } else if (finalGrade < FAIL_GRADE_MAX_EXCLUSIVE) {
                score += FAIL_GRADE_PENALTY;
            }
        }
        return Math.max(-1.0, Math.min(1.0, score));
    }

    public static double affinity(ReviewObservation observation) {
        return affinity(observation.rating(), observation.wouldTakeAgain(), observation.finalGrade());
    }

    /** One profile per user with at least one attributed review, keyed by user id. */
    public static Map<String, TasteProfile> buildAll(List<ReviewObservation> observations, Map<String, UnitInfo> units) {
        Map<String, List<ReviewObservation>> byUser = new LinkedHashMap<>();
        for (ReviewObservation o : observations) {
            if (o.userId() == null) {
                continue;
            }
            byUser.computeIfAbsent(o.userId(), k -> new ArrayList<>()).add(o);
        }
        Map<String, TasteProfile> profiles = new HashMap<>();
        byUser.forEach((userId, reviews) -> profiles.put(userId, buildOne(userId, reviews, units)));
        return profiles;
    }

    public static TasteProfile buildOne(String userId, List<ReviewObservation> reviews, Map<String, UnitInfo> units) {
        Map<String, Double> affinities = new LinkedHashMap<>();
        List<ReviewObservation> liked = new ArrayList<>();
        for (ReviewObservation o : reviews) {
            double a = affinity(o);
            affinities.put(o.unitCode(), a);
            if (a >= LIKED_AFFINITY) {
                liked.add(o);
            }
        }

        Double likedWorkloadMean = null;
        Map<ReviewTag, Double> tagShares = new EnumMap<>(ReviewTag.class);
        if (!liked.isEmpty()) {
            double workloadSum = 0;
            Map<ReviewTag, Integer> tagCounts = new EnumMap<>(ReviewTag.class);
            for (ReviewObservation o : liked) {
                workloadSum += o.workload();
                for (ReviewTag tag : o.tags()) {
                    tagCounts.merge(tag, 1, Integer::sum);
                }
            }
            likedWorkloadMean = workloadSum / liked.size();
            for (Map.Entry<ReviewTag, Integer> e : tagCounts.entrySet()) {
                tagShares.put(e.getKey(), e.getValue() / (double) liked.size());
            }
        }

        Map<Faculty, Integer> facultyCounts = new EnumMap<>(Faculty.class);
        int known = 0;
        for (String code : affinities.keySet()) {
            UnitInfo unit = units.get(code);
            if (unit != null && unit.faculty() != null) {
                facultyCounts.merge(unit.faculty(), 1, Integer::sum);
                known++;
            }
        }
        Map<Faculty, Double> facultyMix = new EnumMap<>(Faculty.class);
        for (Map.Entry<Faculty, Integer> e : facultyCounts.entrySet()) {
            facultyMix.put(e.getKey(), e.getValue() / (double) known);
        }

        return new TasteProfile(userId, affinities, likedWorkloadMean, tagShares, facultyMix);
    }
}

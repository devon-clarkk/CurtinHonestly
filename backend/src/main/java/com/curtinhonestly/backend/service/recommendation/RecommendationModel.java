package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.ReviewTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable in-memory snapshot of everything the recommender derives from the
 * review set: taste profiles, per-unit statistics and item vectors. Built once
 * and shared across requests until the service decides it is stale.
 */
public final class RecommendationModel {

    /** Aggregates over every review of one unit, attributed or anonymised. */
    public record UnitStats(int reviewCount, double meanRating, double meanWorkload, Map<ReviewTag, Double> tagShares) {
        public UnitStats {
            tagShares = Map.copyOf(tagShares);
        }
    }

    private final Map<String, TasteProfile> profiles;
    private final Map<String, UnitInfo> units;
    private final Map<String, Map<String, ReviewObservation>> observationsByUser;
    private final Map<String, UnitStats> unitStats;
    private final Map<String, Map<String, Double>> itemVectors;
    private final double globalMeanRating;
    private final int observationCount;

    private RecommendationModel(Map<String, TasteProfile> profiles,
                                Map<String, UnitInfo> units,
                                Map<String, Map<String, ReviewObservation>> observationsByUser,
                                Map<String, UnitStats> unitStats,
                                Map<String, Map<String, Double>> itemVectors,
                                double globalMeanRating,
                                int observationCount) {
        this.profiles = Collections.unmodifiableMap(profiles);
        this.units = Collections.unmodifiableMap(units);
        this.observationsByUser = Collections.unmodifiableMap(observationsByUser);
        this.unitStats = Collections.unmodifiableMap(unitStats);
        this.itemVectors = Collections.unmodifiableMap(itemVectors);
        this.globalMeanRating = globalMeanRating;
        this.observationCount = observationCount;
    }

    /** Model from reviews alone; no completed-unit signal. */
    public static RecommendationModel build(List<ReviewObservation> observations, Map<String, UnitInfo> units) {
        return build(observations, units, Map.of(), 0.0);
    }

    /**
     * @param completedByUser       userId -> unit codes the student marked as completed;
     *                              those not reviewed enter the taste vector at completedUnitAffinity
     * @param completedUnitAffinity see {@link RecommendationWeights#COMPLETED_UNIT_AFFINITY}
     */
    public static RecommendationModel build(List<ReviewObservation> observations, Map<String, UnitInfo> units,
                                            Map<String, Set<String>> completedByUser, double completedUnitAffinity) {
        Map<String, List<ReviewObservation>> byUnit = new HashMap<>();
        Map<String, Map<String, ReviewObservation>> byUser = new HashMap<>();
        Map<String, Map<String, Double>> itemVectors = new HashMap<>();
        double ratingSum = 0;
        int ratingCount = 0;

        for (ReviewObservation o : observations) {
            byUnit.computeIfAbsent(o.unitCode(), k -> new ArrayList<>()).add(o);
            ratingSum += o.rating();
            ratingCount++;
            if (o.userId() != null) {
                byUser.computeIfAbsent(o.userId(), k -> new LinkedHashMap<>()).put(o.unitCode(), o);
                itemVectors.computeIfAbsent(o.unitCode(), k -> new HashMap<>())
                        .put(o.userId(), TasteProfileBuilder.affinity(o));
            }
        }

        Map<String, UnitStats> stats = new HashMap<>();
        byUnit.forEach((code, list) -> stats.put(code, statsOf(list)));

        Map<String, TasteProfile> profiles =
                TasteProfileBuilder.buildAll(observations, units, completedByUser, completedUnitAffinity);
        double globalMean = ratingCount == 0 ? 0 : ratingSum / ratingCount;

        return new RecommendationModel(profiles, new HashMap<>(units), byUser, stats, itemVectors, globalMean,
                observations.size());
    }

    private static UnitStats statsOf(List<ReviewObservation> reviews) {
        double ratingSum = 0;
        double workloadSum = 0;
        Map<ReviewTag, Integer> tagCounts = new EnumMap<>(ReviewTag.class);
        for (ReviewObservation o : reviews) {
            ratingSum += o.rating();
            workloadSum += o.workload();
            for (ReviewTag tag : o.tags()) {
                tagCounts.merge(tag, 1, Integer::sum);
            }
        }
        int n = reviews.size();
        Map<ReviewTag, Double> shares = new EnumMap<>(ReviewTag.class);
        tagCounts.forEach((tag, count) -> shares.put(tag, count / (double) n));
        return new UnitStats(n, ratingSum / n, workloadSum / n, shares);
    }

    public TasteProfile profile(String userId) {
        return profiles.get(userId);
    }

    public Map<String, TasteProfile> profiles() {
        return profiles;
    }

    public UnitInfo unit(String unitCode) {
        return units.get(unitCode);
    }

    public Map<String, UnitInfo> units() {
        return units;
    }

    public ReviewObservation observation(String userId, String unitCode) {
        Map<String, ReviewObservation> byUnit = observationsByUser.get(userId);
        return byUnit == null ? null : byUnit.get(unitCode);
    }

    public UnitStats unitStats(String unitCode) {
        return unitStats.get(unitCode);
    }

    public Map<String, UnitStats> unitStats() {
        return unitStats;
    }

    /** userId -> affinity for everyone who reviewed the unit; empty when nobody has. */
    public Map<String, Double> itemVector(String unitCode) {
        return itemVectors.getOrDefault(unitCode, Map.of());
    }

    public Map<String, Map<String, Double>> itemVectors() {
        return itemVectors;
    }

    public double globalMeanRating() {
        return globalMeanRating;
    }

    /** Reviews the model was built from, attributed and anonymised. */
    public int observationCount() {
        return observationCount;
    }
}

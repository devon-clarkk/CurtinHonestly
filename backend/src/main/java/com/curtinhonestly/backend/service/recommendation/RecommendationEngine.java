package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.ReviewTag;
import com.curtinhonestly.backend.dto.RecommendationItemDTO;
import com.curtinhonestly.backend.dto.RecommendationSimilarUnitDTO;
import com.curtinhonestly.backend.dto.RecommendationSimilarUnitsDTO;
import com.curtinhonestly.backend.dto.RecommendationUnitMatchDTO;
import com.curtinhonestly.backend.dto.RecommendationUnitMatchDTO.State;
import com.curtinhonestly.backend.dto.RecommendationsDTO;
import com.curtinhonestly.backend.service.recommendation.RecommendationModel.UnitStats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.BAYESIAN_PRIOR_WEIGHT;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.CONFIDENCE_MAX;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.CONFIDENCE_MIN;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.FALLBACK_CONFIDENCE_BASE;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.FALLBACK_CONFIDENCE_MAX;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.FALLBACK_CONFIDENCE_PER_REVIEW;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.HIGH_GRADE_MIN;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.ITEM_OVERLAP_SHRINK;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.LIKED_AFFINITY;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.LIST_LIMIT;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.MAX_ANCHOR_UNITS_IN_REASON;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.MAX_REASONS;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.MIN_REVIEWS_FOR_PERSONALISED;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.RATING_RANGE;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.SIMILAR_MIN_CO_REVIEWERS;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.SIMILAR_MIN_CO_REVIEW_ITEMS;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.SIMILAR_UNITS_LIMIT;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.TAG_PREFERENCE_SHARE;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.WORKLOAD_DIFFERENT_GAP;
import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.WORKLOAD_SIMILAR_TOLERANCE;

/**
 * Collaborative-filtering recommender over a {@link RecommendationModel}.
 * Pure Java: no Spring, no persistence, deterministic for a given model.
 *
 * <p>Privacy rule for generated reasons: the only unit codes that may appear
 * are ones the target student has reviewed or completed themselves. Neighbours
 * are only ever described in aggregate ("4 similar students").
 */
public final class RecommendationEngine {

    private static final String MESSAGE_FEW_REVIEWS =
            "Review at least two units and this page is built from students whose ratings match yours.";
    private static final String MESSAGE_NO_NEIGHBOURS =
            "Personalised picks appear once other students have reviewed some of the same units as you.";
    private static final String FALLBACK_FACULTY_SUFFIX =
            " Until then, here are the highest rated units in the faculties you study.";
    private static final String FALLBACK_GLOBAL_SUFFIX =
            " Until then, here are the highest rated units on CurtinHonestly.";

    private final RecommendationModel model;
    private final RecommendationTuning tuning;

    public RecommendationEngine(RecommendationModel model) {
        this(model, RecommendationTuning.defaults());
    }

    public RecommendationEngine(RecommendationModel model, RecommendationTuning tuning) {
        this.model = Objects.requireNonNull(model);
        this.tuning = Objects.requireNonNull(tuning);
    }

    private record Neighbour(TasteProfile profile, double similarity) {}

    private record Support(Neighbour neighbour, double affinity) {}

    private record Candidate(String unitCode, double predicted, int confidence, List<Support> supports) {}

    /** One row of the full ranking, for the evaluation harness. */
    record ScoredUnit(String unitCode, double predicted, int confidence, int supportingStudents) {}

    // ---------------------------------------------------------------- user

    public RecommendationsDTO recommend(String userId, Set<String> completedUnitCodes) {
        TasteProfile target = model.profile(userId);
        Set<String> completed = normalise(completedUnitCodes);
        int basedOn = target == null ? 0 : target.reviewCount();

        if (target == null || basedOn < MIN_REVIEWS_FOR_PERSONALISED) {
            return coldStart(target, completed, basedOn, 0, MESSAGE_FEW_REVIEWS);
        }

        List<Neighbour> neighbours = findNeighbours(target);
        if (neighbours.isEmpty()) {
            return coldStart(target, completed, basedOn, 0, MESSAGE_NO_NEIGHBOURS);
        }

        Set<String> excluded = excludedUnits(target, completed);
        List<Candidate> candidates = scoreCandidates(neighbours, excluded);

        List<RecommendationItemDTO> recommended = candidates.stream()
                .filter(c -> c.predicted() >= tuning.recommendThreshold())
                .sorted(positiveOrder())
                .limit(LIST_LIMIT)
                .map(c -> toItem(target, c, true))
                .toList();

        List<RecommendationItemDTO> avoid = candidates.stream()
                .filter(c -> c.predicted() <= tuning.avoidThreshold())
                .sorted(Comparator.comparingDouble((Candidate c) -> rankScore(c, false)).reversed()
                        .thenComparingDouble(Candidate::predicted)
                        .thenComparing(Candidate::unitCode))
                .limit(LIST_LIMIT)
                .map(c -> toItem(target, c, false))
                .toList();

        return new RecommendationsDTO(false, null, basedOn, neighbours.size(), recommended, avoid);
    }

    /**
     * How well one unit fits a student, from the same neighbourhood the For You
     * page uses. REVIEWED wins over everything: the student already knows.
     * COLD_START mirrors the page's cold start. NO_SIGNAL means the student has
     * enough reviews but no neighbour has reviewed this unit.
     */
    public RecommendationUnitMatchDTO matchFor(String userId, Set<String> completedUnitCodes, String unitCode) {
        TasteProfile target = model.profile(userId);
        int basedOn = target == null ? 0 : target.reviewCount();
        if (target != null && target.hasReviewed(unitCode)) {
            return RecommendationUnitMatchDTO.of(State.REVIEWED, basedOn);
        }
        if (target == null || basedOn < MIN_REVIEWS_FOR_PERSONALISED) {
            return RecommendationUnitMatchDTO.of(State.COLD_START, basedOn);
        }
        List<Neighbour> neighbours = findNeighbours(target);
        List<Support> supports = new ArrayList<>();
        for (Neighbour neighbour : neighbours) {
            Double affinity = neighbour.profile().affinities().get(unitCode);
            if (affinity != null) {
                supports.add(new Support(neighbour, affinity));
            }
        }
        if (supports.isEmpty()) {
            return RecommendationUnitMatchDTO.of(State.NO_SIGNAL, basedOn);
        }
        Candidate candidate = score(unitCode, supports);
        return new RecommendationUnitMatchDTO(State.MATCH, matchScore(candidate.predicted()), candidate.confidence(),
                supports.size(), reasons(target, candidate, candidate.predicted() >= 0), basedOn);
    }

    /**
     * The full ranking behind the recommended list, best first, without the
     * threshold or the length limit. Empty when the student is a cold start.
     * Package-private for the evaluation harness.
     */
    List<ScoredUnit> rankAll(String userId, Set<String> completedUnitCodes) {
        TasteProfile target = model.profile(userId);
        if (target == null || target.reviewCount() < MIN_REVIEWS_FOR_PERSONALISED) {
            return List.of();
        }
        List<Neighbour> neighbours = findNeighbours(target);
        if (neighbours.isEmpty()) {
            return List.of();
        }
        Set<String> excluded = excludedUnits(target, normalise(completedUnitCodes));
        return scoreCandidates(neighbours, excluded).stream()
                .sorted(positiveOrder())
                .map(c -> new ScoredUnit(c.unitCode(), c.predicted(), c.confidence(), c.supports().size()))
                .toList();
    }

    /** Every unit at least one neighbour reviewed, scored, minus the excluded set. Unordered. */
    private List<Candidate> scoreCandidates(List<Neighbour> neighbours, Set<String> excluded) {
        Map<String, List<Support>> supports = new HashMap<>();
        for (Neighbour neighbour : neighbours) {
            neighbour.profile().affinities().forEach((code, affinity) -> {
                if (!excluded.contains(code)) {
                    supports.computeIfAbsent(code, k -> new ArrayList<>()).add(new Support(neighbour, affinity));
                }
            });
        }
        return supports.entrySet().stream()
                .map(e -> score(e.getKey(), e.getValue()))
                .toList();
    }

    private Comparator<Candidate> positiveOrder() {
        return Comparator.comparingDouble((Candidate c) -> rankScore(c, true)).reversed()
                .thenComparing(Comparator.comparingDouble((Candidate c) -> c.predicted()).reversed())
                .thenComparing(Candidate::unitCode);
    }

    private List<Neighbour> findNeighbours(TasteProfile target) {
        return model.profiles().values().stream()
                .filter(p -> !p.userId().equals(target.userId()))
                .map(p -> new Neighbour(p, UserSimilarity.similarity(target, p, tuning)))
                .filter(n -> n.similarity() > tuning.neighbourMinSimilarity())
                .sorted(Comparator.comparingDouble((Neighbour n) -> n.similarity()).reversed()
                        .thenComparing(n -> n.profile().userId()))
                .limit(tuning.neighbourLimit())
                .toList();
    }

    /**
     * predicted = sum(sim * affinity) / sum(|sim|)
     * confidence = 100 * (1 - exp(-supportMass / scale)) * agreement, clamped 5..99,
     * where supportMass is the summed similarity of supporters and agreement is
     * one minus the standard deviation of their affinities (max spread is 1).
     */
    private Candidate score(String unitCode, List<Support> supports) {
        double weighted = 0;
        double absMass = 0;
        double mass = 0;
        double affinitySum = 0;
        for (Support s : supports) {
            double sim = s.neighbour().similarity();
            weighted += sim * s.affinity();
            absMass += Math.abs(sim);
            mass += sim;
            affinitySum += s.affinity();
        }
        double predicted = absMass == 0 ? 0 : weighted / absMass;

        double mean = affinitySum / supports.size();
        double variance = 0;
        for (Support s : supports) {
            variance += (s.affinity() - mean) * (s.affinity() - mean);
        }
        variance /= supports.size();
        double agreement = clamp(1 - Math.sqrt(variance), 0, 1);

        double confidence = 100 * (1 - Math.exp(-mass / tuning.confidenceMassScale())) * agreement;
        int rounded = (int) Math.round(clamp(confidence, CONFIDENCE_MIN, CONFIDENCE_MAX));
        return new Candidate(unitCode, predicted, rounded, supports);
    }

    /**
     * Order within the recommended (or avoid) list: confidence, optionally
     * weighted by how strongly the unit is predicted in the list's direction.
     */
    private double rankScore(Candidate c, boolean positive) {
        double w = tuning.rankAffinityWeight();
        if (w <= 0) {
            return c.confidence();
        }
        double strength = positive ? (c.predicted() + 1) / 2 : (1 - c.predicted()) / 2;
        return c.confidence() * Math.pow(clamp(strength, 0, 1), w);
    }

    private static int matchScore(double predicted) {
        return (int) Math.round(clamp((predicted + 1) / 2 * 100, 0, 100));
    }

    private RecommendationItemDTO toItem(TasteProfile target, Candidate candidate, boolean positive) {
        UnitInfo info = model.unit(candidate.unitCode());
        return new RecommendationItemDTO(
                candidate.unitCode(),
                nameOf(info, candidate.unitCode()),
                info == null ? "" : info.facultyLabel(),
                info == null ? "" : info.levelLabel(),
                matchScore(candidate.predicted()),
                candidate.confidence(),
                candidate.supports().size(),
                reasons(target, candidate, positive));
    }

    // ------------------------------------------------------------- reasons

    private List<String> reasons(TasteProfile target, Candidate candidate, boolean positive) {
        List<String> reasons = new ArrayList<>();
        List<Support> supports = candidate.supports();
        int n = supports.size();
        String students = n == 1 ? "similar student" : "similar students";

        // 1. Anchor units: the target's own liked units that supporters also liked.
        Map<String, Integer> anchorCounts = new HashMap<>();
        Set<String> targetLiked = target.likedUnits();
        for (Support s : supports) {
            for (String code : targetLiked) {
                if (s.neighbour().profile().affinityOf(code) >= LIKED_AFFINITY) {
                    anchorCounts.merge(code, 1, Integer::sum);
                }
            }
        }
        List<String> anchors = anchorCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_ANCHOR_UNITS_IN_REASON)
                .map(Map.Entry::getKey)
                .toList();
        if (!anchors.isEmpty()) {
            String joined = String.join(" and ", anchors);
            reasons.add(positive
                    ? "Popular with students who liked " + joined
                    : "Rated poorly by students who liked " + joined);
        }

        // 1b. A unit the target completed without reviewing, which supporters
        //     also took (reviewed positively, or completed). Only ever names a
        //     unit from the target's own record.
        if (positive) {
            Map<String, Integer> tookCounts = new HashMap<>();
            for (String code : target.completedUnits()) {
                for (Support s : supports) {
                    TasteProfile p = s.neighbour().profile();
                    if (p.took(code) && p.vector().get(code) > 0) {
                        tookCounts.merge(code, 1, Integer::sum);
                    }
                }
            }
            tookCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .findFirst()
                    .ifPresent(e -> reasons.add("Students who took " + e.getKey() + " also liked this"));
        }

        // 2. Would take again among supporters, plus their grades for later.
        int wouldTakeAgain = 0;
        List<Integer> grades = new ArrayList<>();
        for (Support s : supports) {
            ReviewObservation o = model.observation(s.neighbour().profile().userId(), candidate.unitCode());
            if (o != null) {
                if (o.wouldTakeAgain()) {
                    wouldTakeAgain++;
                }
                if (o.finalGrade() != null) {
                    grades.add(o.finalGrade());
                }
            }
        }
        if (positive && wouldTakeAgain >= 1) {
            if (wouldTakeAgain == n) {
                reasons.add(n == 1
                        ? "1 similar student would take this again"
                        : "All " + n + " similar students would take this again");
            } else {
                reasons.add(wouldTakeAgain + " of " + n + " " + students + " would take this again");
            }
        } else if (!positive && (n - wouldTakeAgain) >= 1) {
            int wouldNot = n - wouldTakeAgain;
            if (wouldNot == n) {
                reasons.add(n == 1
                        ? "1 similar student would not take this again"
                        : "None of the " + n + " similar students would take this again");
            } else {
                reasons.add(wouldNot + " of " + n + " " + students + " would not take this again");
            }
        }

        // 3. Workload relative to the units the target liked.
        UnitStats stats = model.unitStats(candidate.unitCode());
        if (target.likedWorkloadMean() != null && stats != null) {
            double gap = stats.meanWorkload() - target.likedWorkloadMean();
            if (Math.abs(gap) <= WORKLOAD_SIMILAR_TOLERANCE) {
                reasons.add("Similar workload to units you liked");
            } else if (gap >= WORKLOAD_DIFFERENT_GAP) {
                reasons.add("Heavier workload than the units you liked");
            } else if (gap <= -WORKLOAD_DIFFERENT_GAP) {
                reasons.add("Lighter workload than the units you liked");
            }
        }

        // 4. Grades among supporters (positive items only).
        if (positive && grades.size() >= 2) {
            double mean = grades.stream().mapToInt(Integer::intValue).average().orElse(0);
            if (mean >= HIGH_GRADE_MIN) {
                reasons.add("Similar students averaged a distinction or higher");
            }
        }

        // 5. A tag most of the target's liked units carry, and most reviews of this unit carry too.
        if (positive && stats != null) {
            for (ReviewTag tag : ReviewTag.values()) {
                if (target.likedTagShares().getOrDefault(tag, 0.0) >= TAG_PREFERENCE_SHARE
                        && stats.tagShares().getOrDefault(tag, 0.0) >= TAG_PREFERENCE_SHARE) {
                    reasons.add(tag.getDisplayName() + ", like most units you liked");
                    break;
                }
            }
        }

        return reasons.size() > MAX_REASONS ? List.copyOf(reasons.subList(0, MAX_REASONS)) : List.copyOf(reasons);
    }

    // ---------------------------------------------------------- cold start

    private RecommendationsDTO coldStart(TasteProfile target, Set<String> completed, int basedOn,
                                         int neighbourCount, String message) {
        Set<String> excluded = excludedUnits(target, completed);
        Set<Faculty> faculties = new HashSet<>();
        for (String code : excluded) {
            UnitInfo unit = model.unit(code);
            if (unit != null && unit.faculty() != null) {
                faculties.add(unit.faculty());
            }
        }
        boolean scoped = !faculties.isEmpty();
        List<RecommendationItemDTO> items = fallback(excluded, faculties);
        if (items.isEmpty() && scoped) {
            items = fallback(excluded, Set.of());
            scoped = false;
        }
        String fullMessage = message + (scoped ? FALLBACK_FACULTY_SUFFIX : FALLBACK_GLOBAL_SUFFIX);
        return new RecommendationsDTO(true, fullMessage, basedOn, neighbourCount, items, List.of());
    }

    /** Top units by Bayesian-smoothed rating, restricted to the given faculties when non-empty. */
    private List<RecommendationItemDTO> fallback(Set<String> excluded, Set<Faculty> faculties) {
        double c = model.globalMeanRating();
        double m = BAYESIAN_PRIOR_WEIGHT;
        boolean scoped = !faculties.isEmpty();
        record Scored(UnitInfo unit, UnitStats stats, double score) {}

        return model.unitStats().entrySet().stream()
                .filter(e -> !excluded.contains(e.getKey()))
                .map(e -> {
                    UnitInfo unit = model.unit(e.getKey());
                    if (unit == null) {
                        return null;
                    }
                    double v = e.getValue().reviewCount();
                    double r = e.getValue().meanRating();
                    double score = (v / (v + m)) * r + (m / (v + m)) * c;
                    return new Scored(unit, e.getValue(), score);
                })
                .filter(Objects::nonNull)
                .filter(s -> !scoped || faculties.contains(s.unit().faculty()))
                .sorted(Comparator.comparingDouble((Scored s) -> s.score()).reversed()
                        .thenComparing(Comparator.comparingInt((Scored s) -> s.stats().reviewCount()).reversed())
                        .thenComparing(s -> s.unit().code()))
                .limit(LIST_LIMIT)
                .map(s -> {
                    int v = s.stats().reviewCount();
                    int matchScore = (int) Math.round(clamp((s.score() - 1) / RATING_RANGE * 100, 0, 100));
                    int confidence = Math.min(FALLBACK_CONFIDENCE_MAX,
                            FALLBACK_CONFIDENCE_BASE + FALLBACK_CONFIDENCE_PER_REVIEW * v);
                    List<String> reasons = List.of(
                            String.format(Locale.ROOT, "Rated %.1f out of 5 by %d %s",
                                    s.stats().meanRating(), v, v == 1 ? "student" : "students"),
                            scoped ? "Same faculty as units you have reviewed or completed"
                                   : "Among the highest rated units on CurtinHonestly");
                    return new RecommendationItemDTO(s.unit().code(), s.unit().name(), s.unit().facultyLabel(),
                            s.unit().levelLabel(), matchScore, confidence, v, reasons);
                })
                .toList();
    }

    // ---------------------------------------------------------- unit-unit

    /**
     * Units that the same students also rated well. Item-item cosine over shared
     * reviewers (min SIMILAR_MIN_CO_REVIEWERS) shrunk by ITEM_OVERLAP_SHRINK;
     * only units the co-reviewers liked on average qualify. When fewer than
     * SIMILAR_MIN_CO_REVIEW_ITEMS result, the list is topped up with same
     * faculty and level units of the closest average rating.
     */
    public RecommendationSimilarUnitsDTO similarUnits(UnitInfo target) {
        String code = target.code();
        Map<String, Double> targetVector = model.itemVector(code);
        record CoItem(String code, double similarity, int shared) {}
        List<CoItem> coItems = new ArrayList<>();
        // Units with enough co-reviewers whose verdict was negative. The catalogue
        // top-up below must not resurrect them as neighbours.
        Set<String> rejected = new HashSet<>();

        if (!targetVector.isEmpty()) {
            for (Map.Entry<String, Map<String, Double>> other : model.itemVectors().entrySet()) {
                if (other.getKey().equals(code)) {
                    continue;
                }
                double dot = 0;
                double normA = 0;
                double normB = 0;
                double otherSum = 0;
                int shared = 0;
                for (Map.Entry<String, Double> e : targetVector.entrySet()) {
                    Double y = other.getValue().get(e.getKey());
                    if (y == null) {
                        continue;
                    }
                    shared++;
                    double x = e.getValue();
                    dot += x * y;
                    normA += x * x;
                    normB += y * y;
                    otherSum += y;
                }
                if (shared < SIMILAR_MIN_CO_REVIEWERS || normA == 0 || normB == 0) {
                    continue;
                }
                double cosine = dot / (Math.sqrt(normA) * Math.sqrt(normB));
                double similarity = cosine * (shared / (shared + ITEM_OVERLAP_SHRINK));
                if (similarity <= 0 || otherSum / shared < 0) {
                    rejected.add(other.getKey());
                    continue;
                }
                coItems.add(new CoItem(other.getKey(), similarity, shared));
            }
        }

        coItems.sort(Comparator.comparingDouble((CoItem i) -> i.similarity()).reversed()
                .thenComparing(Comparator.comparingInt((CoItem i) -> i.shared()).reversed())
                .thenComparing(CoItem::code));

        List<RecommendationSimilarUnitDTO> items = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>(rejected);
        used.add(code);
        for (CoItem item : coItems) {
            if (items.size() >= SIMILAR_UNITS_LIMIT) {
                break;
            }
            UnitInfo info = model.unit(item.code());
            items.add(new RecommendationSimilarUnitDTO(item.code(), nameOf(info, item.code()),
                    info == null ? "" : info.facultyLabel(), info == null ? "" : info.levelLabel(),
                    (int) Math.round(clamp(item.similarity() * 100, 0, 100)), item.shared()));
            used.add(item.code());
        }
        boolean basedOnCoReviews = !items.isEmpty();

        if (items.size() < SIMILAR_MIN_CO_REVIEW_ITEMS) {
            UnitStats targetStats = model.unitStats(code);
            double targetRating = targetStats != null ? targetStats.meanRating() : target.averageRating();
            record Fallback(UnitInfo unit, double gap, int reviews) {}
            // Only units that have reviews: an unreviewed unit has no rating to be close to.
            List<Fallback> fallbacks = model.units().values().stream()
                    .filter(u -> !used.contains(u.code()))
                    .filter(u -> u.faculty() == target.faculty() && u.level() == target.level())
                    .filter(u -> model.unitStats(u.code()) != null)
                    .map(u -> {
                        UnitStats st = model.unitStats(u.code());
                        return new Fallback(u, Math.abs(st.meanRating() - targetRating), st.reviewCount());
                    })
                    .sorted(Comparator.comparingDouble((Fallback f) -> f.gap())
                            .thenComparing(Comparator.comparingInt((Fallback f) -> f.reviews()).reversed())
                            .thenComparing(f -> f.unit().code()))
                    .limit(SIMILAR_UNITS_LIMIT - items.size())
                    .toList();
            for (Fallback f : fallbacks) {
                int matchScore = (int) Math.round(clamp((1 - f.gap() / RATING_RANGE) * 100, 0, 100));
                items.add(new RecommendationSimilarUnitDTO(f.unit().code(), f.unit().name(), f.unit().facultyLabel(),
                        f.unit().levelLabel(), matchScore, 0));
            }
        }

        return new RecommendationSimilarUnitsDTO(List.copyOf(items), basedOnCoReviews);
    }

    // ------------------------------------------------------------- helpers

    /** Reviewed units, completed units from the request and completed units in the model are never candidates. */
    private static Set<String> excludedUnits(TasteProfile target, Set<String> completed) {
        Set<String> excluded = new HashSet<>(completed);
        if (target != null) {
            excluded.addAll(target.vector().keySet());
        }
        return excluded;
    }

    private static Set<String> normalise(Set<String> codes) {
        Set<String> out = new HashSet<>();
        if (codes == null) {
            return out;
        }
        for (String code : codes) {
            if (code == null) {
                continue;
            }
            String trimmed = code.trim();
            out.add(trimmed);
            out.add(trimmed.toUpperCase(Locale.ROOT));
        }
        return out;
    }

    private static String nameOf(UnitInfo info, String code) {
        return info == null || info.name() == null ? code : info.name();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

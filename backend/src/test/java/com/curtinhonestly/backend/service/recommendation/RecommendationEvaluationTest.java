package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.ReviewTag;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.dto.RecommendationItemDTO;
import com.curtinhonestly.backend.dto.RecommendationUnitMatchDTO;
import com.curtinhonestly.backend.dto.RecommendationUnitMatchDTO.State;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.LIKED_AFFINITY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline evaluation harness. Generates a synthetic student population with
 * planted structure (latent student types with preferred unit clusters, unit
 * quality, grade correlation, noise, real-site sparsity) and measures the
 * recommender by leave-one-out:
 *
 * <ul>
 *   <li>Ranking: hide one liked review per student, ask for the full ranking
 *       and report hit rate at 5 and 10, mean reciprocal rank, and the share of
 *       hidden units that appear in the first ten items of the thresholded
 *       recommended list a student actually sees. Random and popularity
 *       baselines sit beside them.</li>
 *   <li>Calibration: hide one review of any sentiment, ask for the match of
 *       that unit, and bucket the answers by confidence and by match score to
 *       see whether the observed like rate rises with them.</li>
 * </ul>
 *
 * <p>Deterministic (seeded Random) and fast: one configuration takes well under
 * a second. Set the environment variable RECOMMENDATION_SWEEP=true to also run
 * the weight sweep, which prints one line per candidate tuning.
 */
class RecommendationEvaluationTest {

    private static final long SEED = 20260904L;
    private static final int STUDENTS = 400;
    private static final int UNITS = 120;
    private static final int TYPES = 6;
    private static final int UNITS_PER_TYPE = UNITS / TYPES;
    // Reviews per student, sampled uniformly from this list: median 3, mean 3.5,
    // a long tail like the real site.
    private static final int[] REVIEW_COUNTS = {1, 1, 2, 2, 3, 3, 3, 4, 4, 5, 6, 8};
    private static final int[] COMPLETED_COUNTS = {0, 0, 1, 1, 2, 3};
    private static final Faculty[] TYPE_FACULTY = {
            Faculty.SCIENCE_AND_ENGINEERING, Faculty.SCIENCE_AND_ENGINEERING, Faculty.BUSINESS_AND_LAW,
            Faculty.HEALTH_SCIENCES, Faculty.HUMANITIES, Faculty.BUSINESS_AND_LAW};
    private static final ReviewTag[] TYPE_TAG = {
            ReviewTag.PRACTICAL_LABS, ReviewTag.WEEKLY_QUIZZES, ReviewTag.GROUP_WORK,
            ReviewTag.ATTENDANCE_MARKED, ReviewTag.HEAVY_READING, ReviewTag.OPEN_BOOK_EXAM};

    // ------------------------------------------------------------ the world

    private record SyntheticUnit(String code, int type, double quality, double workload, Set<ReviewTag> tags,
                                 double popularity) {}

    private record Student(String id, int primaryType, int secondaryType) {}

    private static final class World {
        final Map<String, UnitInfo> units = new LinkedHashMap<>();
        final Map<String, SyntheticUnit> synthetic = new LinkedHashMap<>();
        final List<Student> students = new ArrayList<>();
        final Map<String, List<ReviewObservation>> reviewsByStudent = new LinkedHashMap<>();
        final Map<String, Set<String>> completedByStudent = new LinkedHashMap<>();
        final List<ReviewObservation> allReviews = new ArrayList<>();
    }

    private static World world;

    @BeforeAll
    static void generate() {
        Random rnd = new Random(SEED);
        world = new World();

        // Units: contiguous clusters of one type each, a quality offset so some
        // units are simply better, a type-flavoured tag set and a Zipf-shaped
        // popularity so a few units in each cluster gather most of the reviews.
        List<Integer> ranks = new ArrayList<>();
        for (int i = 0; i < UNITS; i++) {
            int type = i / UNITS_PER_TYPE;
            if (i % UNITS_PER_TYPE == 0) {
                ranks.clear();
                for (int r = 0; r < UNITS_PER_TYPE; r++) {
                    ranks.add(r);
                }
                java.util.Collections.shuffle(ranks, rnd);
            }
            String code = String.format(Locale.ROOT, "T%dU%02d", type, i % UNITS_PER_TYPE);
            double quality = rnd.nextGaussian() * 0.3;
            double workload = clamp(3 + type * 0.8 + rnd.nextGaussian(), 1, 10);
            Set<ReviewTag> tags = EnumSet.of(TYPE_TAG[type]);
            if (rnd.nextDouble() < 0.4) {
                tags.add(ReviewTag.values()[rnd.nextInt(ReviewTag.values().length)]);
            }
            double popularity = 1.0 / Math.pow(1 + ranks.get(i % UNITS_PER_TYPE), 0.8);
            world.synthetic.put(code, new SyntheticUnit(code, type, quality, workload, tags, popularity));
            world.units.put(code, new UnitInfo(code, "Unit " + code, TYPE_FACULTY[type], UnitLevel.UNDERGRADUATE, 0));
        }

        // Students: a primary type, sometimes a secondary one, a handful of
        // reviews drawn mostly from their own clusters, and a few completed
        // units they never reviewed.
        List<SyntheticUnit> unitList = new ArrayList<>(world.synthetic.values());
        for (int s = 0; s < STUDENTS; s++) {
            int primary = rnd.nextInt(TYPES);
            int secondary = -1;
            if (rnd.nextDouble() < 0.5) {
                do {
                    secondary = rnd.nextInt(TYPES);
                } while (secondary == primary);
            }
            Student student = new Student("s" + s, primary, secondary);
            world.students.add(student);

            int count = REVIEW_COUNTS[rnd.nextInt(REVIEW_COUNTS.length)];
            Set<String> chosen = new HashSet<>();
            List<ReviewObservation> reviews = new ArrayList<>();
            for (int k = 0; k < count; k++) {
                SyntheticUnit unit = pickUnit(rnd, unitList, student, chosen);
                if (unit == null) {
                    break;
                }
                chosen.add(unit.code());
                reviews.add(review(rnd, student, unit));
            }
            world.reviewsByStudent.put(student.id(), reviews);
            world.allReviews.addAll(reviews);

            int completedCount = COMPLETED_COUNTS[rnd.nextInt(COMPLETED_COUNTS.length)];
            Set<String> completed = new HashSet<>();
            for (int k = 0; k < completedCount; k++) {
                SyntheticUnit unit = pickUnit(rnd, unitList, student, chosen);
                if (unit == null) {
                    break;
                }
                chosen.add(unit.code());
                completed.add(unit.code());
            }
            world.completedByStudent.put(student.id(), completed);
        }
    }

    private static SyntheticUnit pickUnit(Random rnd, List<SyntheticUnit> units, Student student, Set<String> taken) {
        double r = rnd.nextDouble();
        int type;
        if (r < 0.7 || student.secondaryType() < 0 && r < 0.9) {
            type = student.primaryType();
        } else if (r < 0.9) {
            type = student.secondaryType();
        } else {
            type = rnd.nextInt(TYPES);
        }
        List<SyntheticUnit> pool = new ArrayList<>();
        double total = 0;
        for (SyntheticUnit u : units) {
            if (u.type() == type && !taken.contains(u.code())) {
                pool.add(u);
                total += u.popularity();
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        double target = rnd.nextDouble() * total;
        for (SyntheticUnit u : pool) {
            target -= u.popularity();
            if (target <= 0) {
                return u;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** Latent affinity in about [-1, 1] from type match, unit quality and noise. */
    private static double latent(Random rnd, Student student, SyntheticUnit unit) {
        double base;
        if (unit.type() == student.primaryType()) {
            base = 0.55;
        } else if (unit.type() == student.secondaryType()) {
            base = 0.25;
        } else {
            base = -0.45;
        }
        return clamp(base + unit.quality() + rnd.nextGaussian() * 0.35, -1, 1);
    }

    private static ReviewObservation review(Random rnd, Student student, SyntheticUnit unit) {
        double latent = latent(rnd, student, unit);
        int rating = (int) clamp(Math.round(3 + 2 * latent), 1, 5);
        boolean wouldTakeAgain = latent + rnd.nextGaussian() * 0.25 > 0;
        Integer grade = rnd.nextDouble() < 0.7
                ? (int) clamp(Math.round(66 + 14 * latent + rnd.nextGaussian() * 8), 30, 98)
                : null;
        int workload = (int) clamp(Math.round(unit.workload() + rnd.nextGaussian()), 1, 10);
        Set<ReviewTag> tags = unit.tags().stream()
                .filter(t -> rnd.nextDouble() < 0.8)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ReviewTag.class)));
        return new ReviewObservation(student.id(), unit.code(), rating, grade, workload, wouldTakeAgain, tags);
    }

    // ----------------------------------------------------------- metrics

    private record RankingResult(int evaluated, int personalised, double hit5, double hit10, double mrr,
                                 double shown10, double popularity10, double random10) {
        String row(String label) {
            return String.format(Locale.ROOT, "%-34s | %4d | %5.2f | %5.3f | %5.3f | %5.3f | %5.3f | %5.3f | %5.3f",
                    label, evaluated, personalised / (double) evaluated, hit5, hit10, mrr, shown10, popularity10,
                    random10);
        }

        static String header() {
            return String.format(Locale.ROOT, "%-34s | %4s | %5s | %5s | %5s | %5s | %5s | %5s | %5s",
                    "tuning", "n", "cov", "hit@5", "hit@10", "MRR", "shn@10", "pop@10", "rnd@10");
        }
    }

    private record Bucket(int n, int liked, int recommended, int recommendedLiked, double scoreSum) {
        double likeRate() {
            return n == 0 ? 0 : liked / (double) n;
        }

        double precision() {
            return recommended == 0 ? 0 : recommendedLiked / (double) recommended;
        }
    }

    private record CalibrationResult(Map<Integer, Bucket> byConfidence, Map<Integer, Bucket> byMatchScore,
                                     int matched, int total) {}

    private static boolean liked(ReviewObservation o) {
        return TasteProfileBuilder.affinity(o) >= LIKED_AFFINITY;
    }

    private static RecommendationModel train(List<ReviewObservation> hidden, RecommendationTuning tuning,
                                             boolean withCompleted) {
        Set<ReviewObservation> hide = new HashSet<>(hidden);
        List<ReviewObservation> train = world.allReviews.stream().filter(o -> !hide.contains(o)).toList();
        return RecommendationModel.build(train, world.units,
                withCompleted ? world.completedByStudent : Map.of(), tuning.completedUnitAffinity());
    }

    private static Set<String> completedFor(String studentId, boolean withCompleted) {
        return withCompleted ? world.completedByStudent.get(studentId) : Set.of();
    }

    private static RankingResult evaluateRanking(RecommendationTuning tuning, boolean withCompleted) {
        Random rnd = new Random(SEED + 1);
        int evaluated = 0;
        int personalised = 0;
        double hit5 = 0;
        double hit10 = 0;
        double mrr = 0;
        double shown10 = 0;
        double pop10 = 0;
        double rand10 = 0;

        for (Student student : world.students) {
            List<ReviewObservation> reviews = world.reviewsByStudent.get(student.id());
            List<ReviewObservation> likedReviews = reviews.stream().filter(RecommendationEvaluationTest::liked).toList();
            if (reviews.size() < 3 || likedReviews.isEmpty()) {
                continue;
            }
            ReviewObservation hidden = likedReviews.get(rnd.nextInt(likedReviews.size()));
            evaluated++;

            RecommendationModel model = train(List.of(hidden), tuning, withCompleted);
            RecommendationEngine engine = new RecommendationEngine(model, tuning);
            Set<String> completed = completedFor(student.id(), withCompleted);

            List<RecommendationEngine.ScoredUnit> ranking = engine.rankAll(student.id(), completed);
            if (!ranking.isEmpty()) {
                personalised++;
                int rank = -1;
                for (int i = 0; i < ranking.size(); i++) {
                    if (ranking.get(i).unitCode().equals(hidden.unitCode())) {
                        rank = i + 1;
                        break;
                    }
                }
                if (rank > 0) {
                    if (rank <= 5) {
                        hit5++;
                    }
                    if (rank <= 10) {
                        hit10++;
                    }
                    mrr += 1.0 / rank;
                }
                List<RecommendationItemDTO> shown = engine.recommend(student.id(), completed).recommended();
                for (int i = 0; i < Math.min(10, shown.size()); i++) {
                    if (shown.get(i).unitCode().equals(hidden.unitCode())) {
                        shown10++;
                        break;
                    }
                }
            }

            // Baselines over the same candidate set: every unit the student has
            // not reviewed or completed.
            Set<String> known = reviews.stream().map(ReviewObservation::unitCode).collect(Collectors.toSet());
            known.remove(hidden.unitCode());
            known.addAll(completed);
            int candidates = UNITS - known.size();
            rand10 += Math.min(1.0, 10.0 / candidates);

            Map<String, Integer> likes = new HashMap<>();
            for (ReviewObservation o : world.allReviews) {
                if (o != hidden && liked(o) && !known.contains(o.unitCode())) {
                    likes.merge(o.unitCode(), 1, Integer::sum);
                }
            }
            List<String> popular = likes.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .toList();
            if (popular.contains(hidden.unitCode())) {
                pop10++;
            }
        }
        return new RankingResult(evaluated, personalised, hit5 / evaluated, hit10 / evaluated, mrr / evaluated,
                shown10 / evaluated, pop10 / evaluated, rand10 / evaluated);
    }

    private static CalibrationResult evaluateCalibration(RecommendationTuning tuning, boolean withCompleted) {
        Random rnd = new Random(SEED + 2);
        Map<Integer, Bucket> byConfidence = new java.util.TreeMap<>();
        Map<Integer, Bucket> byScore = new java.util.TreeMap<>();
        int total = 0;
        int matched = 0;
        int recommendScore = (int) Math.round((tuning.recommendThreshold() + 1) / 2 * 100);

        for (Student student : world.students) {
            List<ReviewObservation> reviews = world.reviewsByStudent.get(student.id());
            if (reviews.size() < 3) {
                continue;
            }
            ReviewObservation hidden = reviews.get(rnd.nextInt(reviews.size()));
            total++;
            RecommendationModel model = train(List.of(hidden), tuning, withCompleted);
            RecommendationUnitMatchDTO match = new RecommendationEngine(model, tuning)
                    .matchFor(student.id(), completedFor(student.id(), withCompleted), hidden.unitCode());
            if (match.state() != State.MATCH) {
                continue;
            }
            matched++;
            boolean actualLiked = liked(hidden);
            boolean recommended = match.matchScore() >= recommendScore;
            record(byConfidence, Math.min(9, match.confidence() / 10), actualLiked, recommended, match.matchScore());
            record(byScore, Math.min(9, match.matchScore() / 10), actualLiked, recommended, match.matchScore());
        }
        return new CalibrationResult(byConfidence, byScore, matched, total);
    }

    private static void record(Map<Integer, Bucket> buckets, int key, boolean liked, boolean recommended, int score) {
        Bucket b = buckets.getOrDefault(key, new Bucket(0, 0, 0, 0, 0));
        buckets.put(key, new Bucket(b.n() + 1, b.liked() + (liked ? 1 : 0), b.recommended() + (recommended ? 1 : 0),
                b.recommendedLiked() + (recommended && liked ? 1 : 0), b.scoreSum() + score));
    }

    private static void printCalibration(String title, CalibrationResult result) {
        System.out.println(title + ": " + result.matched() + " of " + result.total() + " hidden reviews got a MATCH");
        System.out.println(String.format(Locale.ROOT, "  %-11s | %4s | %8s | %9s | %6s", "confidence", "n", "likeRate",
                "precision", "score"));
        result.byConfidence().forEach((k, b) -> System.out.println(String.format(Locale.ROOT,
                "  %2d-%-8d | %4d | %8.2f | %9.2f | %6.1f", k * 10, k * 10 + 9, b.n(), b.likeRate(), b.precision(),
                b.n() == 0 ? 0 : b.scoreSum() / b.n())));
        System.out.println(String.format(Locale.ROOT, "  %-11s | %4s | %8s", "matchScore", "n", "likeRate"));
        result.byMatchScore().forEach((k, b) -> System.out.println(String.format(Locale.ROOT,
                "  %2d-%-8d | %4d | %8.2f", k * 10, k * 10 + 9, b.n(), b.likeRate())));
    }

    /** Weighted like rate over the buckets whose key is in [from, to]. */
    private static double likeRate(Map<Integer, Bucket> buckets, int from, int to) {
        int n = 0;
        int liked = 0;
        for (Map.Entry<Integer, Bucket> e : buckets.entrySet()) {
            if (e.getKey() >= from && e.getKey() <= to) {
                n += e.getValue().n();
                liked += e.getValue().liked();
            }
        }
        return n == 0 ? 0 : liked / (double) n;
    }

    private static double precision(Map<Integer, Bucket> buckets, int from, int to) {
        int n = 0;
        int liked = 0;
        for (Map.Entry<Integer, Bucket> e : buckets.entrySet()) {
            if (e.getKey() >= from && e.getKey() <= to) {
                n += e.getValue().recommended();
                liked += e.getValue().recommendedLiked();
            }
        }
        return n == 0 ? 0 : liked / (double) n;
    }

    // ------------------------------------------------------------- tests

    @Test
    void populationLooksLikeTheRealSite() {
        List<Integer> counts = world.reviewsByStudent.values().stream().map(List::size).sorted().toList();
        int median = counts.get(counts.size() / 2);
        long liked = world.allReviews.stream().filter(RecommendationEvaluationTest::liked).count();
        System.out.println(String.format(Locale.ROOT,
                "population: %d students, %d units, %d reviews, median %d reviews per student, %.0f%% liked",
                STUDENTS, UNITS, world.allReviews.size(), median, 100.0 * liked / world.allReviews.size()));
        assertThat(median).isEqualTo(3);
        assertThat(world.allReviews.size()).isBetween(1200, 1600);
    }

    @Test
    void defaultsBeatTheBaselinesByAClearMargin() {
        long start = System.nanoTime();
        RecommendationTuning defaults = RecommendationTuning.defaults();
        RankingResult withCompleted = evaluateRanking(defaults, true);
        RankingResult reviewsOnly = evaluateRanking(defaults, false);
        System.out.println(RankingResult.header());
        System.out.println(withCompleted.row("defaults"));
        System.out.println(reviewsOnly.row("defaults, completed units ignored"));
        System.out.println(String.format(Locale.ROOT, "ranking evaluation took %d ms", (System.nanoTime() - start) / 1_000_000));

        // Loose floors: the point is to catch a regression that makes the model
        // no better than guessing, not to pin the exact number.
        assertThat(withCompleted.personalised() / (double) withCompleted.evaluated()).isGreaterThan(0.8);
        assertThat(withCompleted.hit10()).isGreaterThan(withCompleted.random10() * 2.5);
        assertThat(withCompleted.hit10()).isGreaterThan(withCompleted.popularity10() + 0.05);
        assertThat(withCompleted.mrr()).isGreaterThan(0.1);
        // The completed-unit signal must not cost accuracy.
        assertThat(withCompleted.hit10()).isGreaterThanOrEqualTo(reviewsOnly.hit10() - 0.02);
    }

    @Test
    void confidenceAndMatchScoreAreCalibrated() {
        CalibrationResult result = evaluateCalibration(RecommendationTuning.defaults(), true);
        printCalibration("calibration (defaults)", result);

        assertThat(result.matched()).isGreaterThan(result.total() / 2);
        // Match score: the top buckets are liked far more often than the bottom ones.
        double lowScore = likeRate(result.byMatchScore(), 0, 4);
        double highScore = likeRate(result.byMatchScore(), 7, 9);
        assertThat(highScore).isGreaterThan(lowScore + 0.25);
        // Confidence: among units the model would recommend, the confident half
        // is right at least as often as the unsure half.
        double lowConfidence = precision(result.byConfidence(), 0, 4);
        double highConfidence = precision(result.byConfidence(), 5, 9);
        assertThat(highConfidence).isGreaterThanOrEqualTo(lowConfidence - 0.03);
    }

    /**
     * Weight sweep, off by default: RECOMMENDATION_SWEEP=true ./gradlew test
     * --tests '*RecommendationEvaluationTest*'. Prints one ranking row per
     * candidate tuning plus the calibration split for each.
     */
    @Test
    void sweep() {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("RECOMMENDATION_SWEEP")), "sweep disabled");
        RecommendationTuning base = RecommendationTuning.defaults();
        Map<String, RecommendationTuning> candidates = new LinkedHashMap<>();
        candidates.put("defaults", base);
        for (double shrink : new double[] {1.0, 3.0, 4.0}) {
            candidates.put("shrink=" + shrink, base.withOverlapShrink(shrink));
        }
        for (double profile : new double[] {0.0, 0.05, 0.3}) {
            candidates.put("profile=" + profile, base.withProfileWeight(profile));
        }
        for (int limit : new int[] {10, 15, 40, 80}) {
            candidates.put("neighbours=" + limit, base.withNeighbourLimit(limit));
        }
        for (double min : new double[] {0.0, 0.05, 0.2}) {
            candidates.put("minSim=" + min, base.withNeighbourMinSimilarity(min));
        }
        for (double threshold : new double[] {0.2, 0.3, 0.45}) {
            candidates.put("threshold=" + threshold, base.withRecommendThreshold(threshold));
        }
        for (double scale : new double[] {0.5, 1.5, 2.0}) {
            candidates.put("confScale=" + scale, base.withConfidenceMassScale(scale));
        }
        for (double weight : new double[] {0.5, 1.0, 2.0}) {
            candidates.put("rankAffinity=" + weight, base.withRankAffinityWeight(weight));
        }
        for (double completed : new double[] {0.0, 0.3, 0.5}) {
            candidates.put("completed=" + completed, base.withCompletedUnitAffinity(completed));
        }
        // Round two: combinations around the single-knob winners.
        for (double scale : new double[] {2.5, 3.0, 4.0}) {
            candidates.put("confScale=" + scale, base.withConfidenceMassScale(scale));
        }
        for (double shrink : new double[] {3.0, 4.0}) {
            for (double scale : new double[] {2.0, 3.0}) {
                candidates.put("shrink=" + shrink + " confScale=" + scale,
                        base.withOverlapShrink(shrink).withConfidenceMassScale(scale));
            }
        }
        for (int limit : new int[] {15, 20}) {
            candidates.put("neighbours=" + limit + " confScale=2.0",
                    base.withNeighbourLimit(limit).withConfidenceMassScale(2.0));
        }
        candidates.put("profile=0.1 confScale=2.0", base.withProfileWeight(0.1).withConfidenceMassScale(2.0));
        for (double completed : new double[] {0.0, 0.3}) {
            candidates.put("completed=" + completed + " confScale=2.0",
                    base.withCompletedUnitAffinity(completed).withConfidenceMassScale(2.0));
        }

        System.out.println(RankingResult.header());
        Map<String, CalibrationResult> calibrations = new LinkedHashMap<>();
        candidates.forEach((label, tuning) -> {
            System.out.println(evaluateRanking(tuning, true).row(label));
            calibrations.put(label, evaluateCalibration(tuning, true));
        });
        System.out.println(String.format(Locale.ROOT, "%-34s | %7s | %8s | %8s | %8s | %8s", "tuning", "matched",
                "lowScore", "hiScore", "lowConf", "hiConf"));
        calibrations.forEach((label, c) -> System.out.println(String.format(Locale.ROOT,
                "%-34s | %7d | %8.2f | %8.2f | %8.2f | %8.2f", label, c.matched(),
                likeRate(c.byMatchScore(), 0, 4), likeRate(c.byMatchScore(), 7, 9),
                precision(c.byConfidence(), 0, 4), precision(c.byConfidence(), 5, 9))));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}

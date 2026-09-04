package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.RecommendationSimilarUnitsDTO;
import com.curtinhonestly.backend.dto.RecommendationStatsDTO;
import com.curtinhonestly.backend.dto.RecommendationUnitMatchDTO;
import com.curtinhonestly.backend.dto.RecommendationsDTO;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.curtinhonestly.backend.service.recommendation.RecommendationWeights.MIN_REVIEWS_FOR_PERSONALISED;

/**
 * Spring entry point for the recommender. Loads every review once, builds a
 * {@link RecommendationModel} and keeps it for {@link #MODEL_TTL}.
 *
 * <p>Freshness: ReviewService calls {@link #invalidateAfterCommit()} whenever a
 * review is created, edited or deleted, so the next request rebuilds the model
 * from the committed data. The TTL is the ceiling for every other change (an
 * account deletion that removes reviews, a completed-units update). The dataset
 * is small (low thousands of reviews) so a rebuild is two queries plus a few
 * milliseconds of arithmetic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RecommendationService {

    static final Duration MODEL_TTL = Duration.ofMinutes(10);

    private final ReviewRepo reviewRepo;
    private final UnitRepo unitRepo;
    private final UserRepo userRepo;
    private final UserService userService;

    /** One built model plus its lazily computed admin statistics. */
    private static final class Snapshot {
        final RecommendationModel model;
        final Instant builtAt;
        volatile RecommendationStatsDTO stats;

        Snapshot(RecommendationModel model, Instant builtAt) {
            this.model = model;
            this.builtAt = builtAt;
        }
    }

    private volatile Snapshot snapshot;

    public RecommendationsDTO recommendationsForCurrentUser() {
        User user = currentUser();
        return new RecommendationEngine(model()).recommend(user.getId(), user.getCompletedUnitCodes());
    }

    /** Empty when no unit has that code. */
    public Optional<RecommendationUnitMatchDTO> matchForCurrentUser(String code) {
        User user = currentUser();
        return unitRepo.findByCode(code)
                .map(unit -> new RecommendationEngine(model())
                        .matchFor(user.getId(), user.getCompletedUnitCodes(), unit.getCode()));
    }

    /** Empty when no unit has that code. */
    public Optional<RecommendationSimilarUnitsDTO> similarUnits(String code) {
        return unitRepo.findByCode(code)
                .map(unit -> new RecommendationEngine(model()).similarUnits(toUnitInfo(unit)));
    }

    /** Shape of the model currently serving requests; builds one when none is cached. */
    public RecommendationStatsDTO stats() {
        Snapshot current = currentSnapshot();
        RecommendationStatsDTO stats = current.stats;
        if (stats == null) {
            stats = computeStats(current.model, current.builtAt);
            current.stats = stats;
        }
        return stats;
    }

    /** Drops the cached model so the next request rebuilds it from the database. */
    public void invalidate() {
        snapshot = null;
    }

    /**
     * Drops the cached model once the calling transaction has committed, so a
     * concurrent request cannot rebuild from data that is about to change and
     * cache the stale result. Outside a transaction it invalidates immediately.
     */
    public void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate();
                }
            });
        } else {
            invalidate();
        }
    }

    RecommendationModel model() {
        return currentSnapshot().model;
    }

    private Snapshot currentSnapshot() {
        Snapshot current = snapshot;
        if (isFresh(current)) {
            return current;
        }
        synchronized (this) {
            current = snapshot;
            if (isFresh(current)) {
                return current;
            }
            Snapshot built = new Snapshot(buildModel(), Instant.now());
            snapshot = built;
            return built;
        }
    }

    private static boolean isFresh(Snapshot s) {
        return s != null && Duration.between(s.builtAt, Instant.now()).compareTo(MODEL_TTL) < 0;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.getUserByEmail(email);
    }

    private RecommendationModel buildModel() {
        long start = System.nanoTime();
        List<Review> reviews = reviewRepo.findAllWithUnitAndUser();
        Map<String, UnitInfo> units = new HashMap<>();
        List<ReviewObservation> observations = reviews.stream()
                .filter(r -> r.getUnit() != null && r.getUnit().getCode() != null)
                .map(r -> {
                    units.computeIfAbsent(r.getUnit().getCode(), k -> toUnitInfo(r.getUnit()));
                    return toObservation(r);
                })
                .toList();
        Map<String, Set<String>> completedByUser = loadCompletedUnits();
        RecommendationModel model = RecommendationModel.build(observations, units, completedByUser,
                RecommendationWeights.COMPLETED_UNIT_AFFINITY);
        log.info("Recommendation model built from {} reviews, {} units, {} profiles, {} students with completed units in {} ms",
                observations.size(), units.size(), model.profiles().size(), completedByUser.size(),
                (System.nanoTime() - start) / 1_000_000);
        return model;
    }

    /** userId -> completed unit codes, upper-cased, for every user who recorded any. */
    private Map<String, Set<String>> loadCompletedUnits() {
        Map<String, Set<String>> byUser = new HashMap<>();
        for (Object[] row : userRepo.findAllCompletedUnitCodes()) {
            if (row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            String code = row[1].toString().trim().toUpperCase(Locale.ROOT);
            if (!code.isEmpty()) {
                byUser.computeIfAbsent(row[0].toString(), k -> new HashSet<>()).add(code);
            }
        }
        return byUser;
    }

    /**
     * Neighbourhood statistics over every profile: O(users squared) similarity
     * calls, each over a handful of units. Computed once per snapshot, on the
     * first admin request for it.
     */
    private static RecommendationStatsDTO computeStats(RecommendationModel model, Instant builtAt) {
        RecommendationTuning tuning = RecommendationTuning.defaults();
        List<TasteProfile> profiles = List.copyOf(model.profiles().values());
        int withNeighbours = 0;
        int coldStart = 0;
        long neighbourTotal = 0;
        for (TasteProfile target : profiles) {
            if (target.reviewCount() < MIN_REVIEWS_FOR_PERSONALISED) {
                coldStart++;
                continue;
            }
            int neighbours = 0;
            for (TasteProfile other : profiles) {
                if (other != target && UserSimilarity.similarity(target, other, tuning) > tuning.neighbourMinSimilarity()) {
                    neighbours++;
                }
            }
            if (neighbours == 0) {
                coldStart++;
            } else {
                withNeighbours++;
                neighbourTotal += Math.min(neighbours, tuning.neighbourLimit());
            }
        }
        double mean = withNeighbours == 0 ? 0 : (double) neighbourTotal / withNeighbours;

        Set<String> pairs = new HashSet<>();
        for (TasteProfile p : profiles) {
            List<String> codes = p.affinities().keySet().stream().sorted().toList();
            for (int i = 0; i < codes.size(); i++) {
                for (int j = i + 1; j < codes.size(); j++) {
                    pairs.add(codes.get(i) + "|" + codes.get(j));
                }
            }
        }

        return new RecommendationStatsDTO(builtAt, model.observationCount(), profiles.size(),
                model.unitStats().size(), withNeighbours, coldStart, Math.round(mean * 10) / 10.0, pairs.size());
    }

    private static ReviewObservation toObservation(Review r) {
        return new ReviewObservation(
                r.getUser() == null ? null : r.getUser().getId(),
                r.getUnit().getCode(),
                r.getRating(),
                r.getFinalGrade(),
                r.getWorkload(),
                r.isWouldTakeAgain(),
                r.getTags());
    }

    private static UnitInfo toUnitInfo(Unit unit) {
        return new UnitInfo(unit.getCode(), unit.getName(), unit.getFaculty(), unit.getLevel(), unit.getAverageRating());
    }
}

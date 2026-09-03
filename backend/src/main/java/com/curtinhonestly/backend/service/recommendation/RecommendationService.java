package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.RecommendationSimilarUnitsDTO;
import com.curtinhonestly.backend.dto.RecommendationsDTO;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring entry point for the recommender. Loads every review once, builds a
 * {@link RecommendationModel} and keeps it for {@link #MODEL_TTL}.
 *
 * <p>Freshness: a review written, edited or deleted is not reflected in
 * recommendations until the snapshot expires (at most ten minutes) or
 * {@link #invalidate()} is called. Nothing calls invalidate yet; the review
 * write paths can do so once immediate refresh is wanted. The dataset is small
 * (low thousands of reviews) so a rebuild is a single query plus a few
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
    private final UserService userService;

    private record Snapshot(RecommendationModel model, Instant builtAt) {}

    private volatile Snapshot snapshot;

    public RecommendationsDTO recommendationsForCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.getUserByEmail(email);
        return new RecommendationEngine(model()).recommend(user.getId(), user.getCompletedUnitCodes());
    }

    /** Empty when no unit has that code. */
    public Optional<RecommendationSimilarUnitsDTO> similarUnits(String code) {
        return unitRepo.findByCode(code)
                .map(unit -> new RecommendationEngine(model()).similarUnits(toUnitInfo(unit)));
    }

    /** Drops the cached model so the next request rebuilds it from the database. */
    public void invalidate() {
        snapshot = null;
    }

    RecommendationModel model() {
        Snapshot current = snapshot;
        if (isFresh(current)) {
            return current.model();
        }
        synchronized (this) {
            current = snapshot;
            if (isFresh(current)) {
                return current.model();
            }
            RecommendationModel built = buildModel();
            snapshot = new Snapshot(built, Instant.now());
            return built;
        }
    }

    private static boolean isFresh(Snapshot s) {
        return s != null && Duration.between(s.builtAt(), Instant.now()).compareTo(MODEL_TTL) < 0;
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
        RecommendationModel model = RecommendationModel.build(observations, units);
        log.info("Recommendation model built from {} reviews, {} units, {} profiles in {} ms",
                observations.size(), units.size(), model.profiles().size(),
                (System.nanoTime() - start) / 1_000_000);
        return model;
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

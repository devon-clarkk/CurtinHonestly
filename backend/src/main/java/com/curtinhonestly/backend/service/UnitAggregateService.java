package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@Transactional(rollbackOn = Exception.class)
@RequiredArgsConstructor
public class UnitAggregateService {

    private final ReviewRepo reviewRepo;
    private final UnitRepo unitRepo;

    public void recalculateForUnit(String unitId) {
        Unit unit = unitRepo.findById(unitId)
                .orElseThrow(() -> new RuntimeException("Unit not found: " + unitId));

        List<Review> reviews = reviewRepo.findByUnit_Id(unitId);

        if (reviews.isEmpty()) {
            unit.setReviewCount(0);
            unit.setAverageRating(0);
            unit.setAverageWorkload(0);
            unit.setAverageFinalGrade(0);
            unit.setWouldTakeAgainRatio(0);
            unit.setLatestReviewAt(null);
        } else {
            unit.setReviewCount(reviews.size());
            unit.setAverageRating(roundTo1Decimal(
                    reviews.stream().mapToInt(Review::getRating).average().orElse(0)));
            unit.setAverageWorkload(roundTo1Decimal(
                    reviews.stream().mapToInt(Review::getWorkload).average().orElse(0)));
            unit.setAverageFinalGrade(roundTo1Decimal(
                    reviews.stream().map(Review::getFinalGrade).filter(Objects::nonNull)
                            .mapToInt(Integer::intValue).average().orElse(0)));
            long positiveCount = reviews.stream().filter(Review::isWouldTakeAgain).count();
            unit.setWouldTakeAgainRatio(roundTo1Decimal((positiveCount * 100.0) / reviews.size()));
            Instant latest = reviews.stream().map(Review::getCreatedAt).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            unit.setLatestReviewAt(latest);
        }

        unitRepo.save(unit);
    }

    public void recalculateAll() {
        List<String> unitIds = unitRepo.findAll().stream().map(Unit::getId).toList();
        log.info("Starting aggregate backfill for {} units", unitIds.size());
        for (String id : unitIds) {
            recalculateForUnit(id);
        }
        log.info("Aggregate backfill complete");
    }

    private double roundTo1Decimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

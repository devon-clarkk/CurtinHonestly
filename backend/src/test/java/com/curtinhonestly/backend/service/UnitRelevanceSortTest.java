package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.dto.UnitSummaryDTO;
import com.curtinhonestly.backend.repo.UnitRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the relevanceScore @Formula against a real Postgres.
 *
 * The formula is native SQL rendered by Hibernate into ORDER BY, so it cannot be
 * verified by compiling or by reading it - it either produces the intended
 * ordering against a real database or it does not.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class UnitRelevanceSortTest {

    @Autowired
    private UnitService unitService;

    @Autowired
    private UnitRepo unitRepo;

    private static final String PREFIX = "ZREL";

    @BeforeEach
    void clearFixtures() {
        unitRepo.deleteAll(unitRepo.findAll().stream()
                .filter(u -> u.getCode().startsWith(PREFIX))
                .toList());
    }

    private void unit(String code, int reviewCount, Instant latestReviewAt) {
        Unit unit = new Unit();
        unit.setCode(code);
        unit.setName("Relevance fixture " + code);
        unit.setDescription("Fixture for relevance ordering.");
        unit.setFaculty(Faculty.SCIENCE_AND_ENGINEERING);
        unit.setLevel(UnitLevel.UNDERGRADUATE);
        unit.setReviewCount(reviewCount);
        unit.setLatestReviewAt(latestReviewAt);
        unitRepo.saveAndFlush(unit);
    }

    private List<String> codesFor(String sortBy) {
        Page<UnitSummaryDTO> page = unitService.getAllUnits(0, 100, PREFIX, null, null, sortBy);
        return page.getContent().stream().map(UnitSummaryDTO::getCode).toList();
    }

    @Test
    void freshReviewsOutrankASlightlyLargerButStalerPile() {
        Instant now = Instant.now();
        unit(PREFIX + "0001", 9, now.minus(365, ChronoUnit.DAYS));
        unit(PREFIX + "0002", 8, now);

        // 8 reviews from today beat 9 from a year ago - the whole point of
        // combining count with recency rather than sorting on count alone.
        assertThat(codesFor("relevance")).containsExactly(PREFIX + "0002", PREFIX + "0001");
    }

    @Test
    void countStillWinsWhenRecencyIsEqual() {
        Instant now = Instant.now();
        unit(PREFIX + "0001", 3, now);
        unit(PREFIX + "0002", 12, now);

        assertThat(codesFor("relevance")).containsExactly(PREFIX + "0002", PREFIX + "0001");
    }

    @Test
    void recencyAloneCannotRescueAUnitNobodyReviewed() {
        Instant now = Instant.now();
        unit(PREFIX + "0001", 0, now);
        unit(PREFIX + "0002", 1, now.minus(3 * 365, ChronoUnit.DAYS));

        // Count multiplies through, so zero reviews scores zero no matter how
        // recent latestReviewAt claims to be.
        assertThat(codesFor("relevance")).containsExactly(PREFIX + "0002", PREFIX + "0001");
    }

    @Test
    void unreviewedUnitsAllTieAndFallBackToCodeOrder() {
        // The situation on prod today: nothing has any reviews, so the tiebreak
        // decides the entire ordering and must be stable.
        unit(PREFIX + "0003", 0, null);
        unit(PREFIX + "0001", 0, null);
        unit(PREFIX + "0002", 0, null);

        assertThat(codesFor("relevance"))
                .containsExactly(PREFIX + "0001", PREFIX + "0002", PREFIX + "0003");
    }

    @Test
    void anUnknownSortFallsBackToRelevanceRatherThanFailing() {
        Instant now = Instant.now();
        unit(PREFIX + "0001", 1, now.minus(365, ChronoUnit.DAYS));
        unit(PREFIX + "0002", 5, now);

        assertThat(codesFor("not-a-real-sort")).containsExactly(PREFIX + "0002", PREFIX + "0001");
        assertThat(codesFor(null)).containsExactly(PREFIX + "0002", PREFIX + "0001");
    }

    @Test
    void explicitSortsStillWorkAndAreStablyTieBroken() {
        Instant now = Instant.now();
        unit(PREFIX + "0001", 5, now);
        unit(PREFIX + "0002", 5, now);
        unit(PREFIX + "0003", 9, now);

        assertThat(codesFor("code")).containsExactly(PREFIX + "0001", PREFIX + "0002", PREFIX + "0003");
        assertThat(codesFor("code_desc")).containsExactly(PREFIX + "0003", PREFIX + "0002", PREFIX + "0001");
        // 0001 and 0002 tie on count; the code tiebreak has to order them.
        assertThat(codesFor("most_reviewed"))
                .containsExactly(PREFIX + "0003", PREFIX + "0001", PREFIX + "0002");
    }
}

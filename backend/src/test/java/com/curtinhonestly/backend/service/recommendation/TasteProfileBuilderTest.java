package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.ReviewTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.BUS;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.SCI;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.love;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.unit;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.units;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TasteProfileBuilderTest {

    @Test
    void ratingMapsLinearlyAndSignalsNudgeIt() {
        // 3 stars is neutral; would-not-take-again pulls it to -0.3
        assertThat(TasteProfileBuilder.affinity(3, false, null)).isCloseTo(-0.3, within(1e-9));
        // 4 stars (+0.5), would take again (+0.3), grade 70 (+0.1)
        assertThat(TasteProfileBuilder.affinity(4, true, 70)).isCloseTo(0.9, within(1e-9));
        // 2 stars (-0.5), would not take again (-0.3), grade 60 (no change)
        assertThat(TasteProfileBuilder.affinity(2, false, 60)).isCloseTo(-0.8, within(1e-9));
        // 5 stars (+1.0), would take again (+0.3), grade 75 (+0.2) clamps to 1
        assertThat(TasteProfileBuilder.affinity(5, true, 75)).isEqualTo(1.0);
        // 1 star (-1.0), would not take again, failed: clamps to -1
        assertThat(TasteProfileBuilder.affinity(1, false, 30)).isEqualTo(-1.0);
        // 5 stars but would not take again and no grade: 0.7
        assertThat(TasteProfileBuilder.affinity(5, false, null)).isCloseTo(0.7, within(1e-9));
    }

    @Test
    void profileSummarisesLikedUnitsOnly() {
        Map<String, UnitInfo> units = units(unit("A1", SCI), unit("A2", SCI), unit("B1", BUS));
        List<ReviewObservation> reviews = List.of(
                new ReviewObservation("u", "A1", 5, 80, 4, true, Set.of(ReviewTag.GROUP_WORK, ReviewTag.PRACTICAL_LABS)),
                new ReviewObservation("u", "A2", 4, null, 6, true, Set.of(ReviewTag.GROUP_WORK)),
                new ReviewObservation("u", "B1", 1, 45, 9, false, Set.of(ReviewTag.HEAVY_READING)));

        TasteProfile profile = TasteProfileBuilder.buildOne("u", reviews, units);

        assertThat(profile.reviewCount()).isEqualTo(3);
        assertThat(profile.likedUnits()).containsExactlyInAnyOrder("A1", "A2");
        assertThat(profile.dislikedUnits()).containsExactly("B1");
        // Workload mean over liked units only: (4 + 6) / 2
        assertThat(profile.likedWorkloadMean()).isCloseTo(5.0, within(1e-9));
        assertThat(profile.likedTagShares()).containsEntry(ReviewTag.GROUP_WORK, 1.0)
                .containsEntry(ReviewTag.PRACTICAL_LABS, 0.5)
                .doesNotContainKey(ReviewTag.HEAVY_READING);
        assertThat(profile.facultyMix().get(SCI)).isCloseTo(2.0 / 3, within(1e-9));
        assertThat(profile.facultyMix().get(BUS)).isCloseTo(1.0 / 3, within(1e-9));
    }

    @Test
    void anonymisedReviewsBuildNoProfile() {
        Map<String, UnitInfo> units = units(unit("A1", SCI));
        Map<String, TasteProfile> profiles = TasteProfileBuilder.buildAll(
                List.of(love("u", "A1"), love(null, "A1")), units);
        assertThat(profiles).containsOnlyKeys("u");
    }
}

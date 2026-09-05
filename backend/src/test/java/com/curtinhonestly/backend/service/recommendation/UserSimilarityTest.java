package com.curtinhonestly.backend.service.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.SCI;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.hate;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.love;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.unit;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.units;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class UserSimilarityTest {

    private static final Map<String, UnitInfo> UNITS =
            units(unit("U1", SCI), unit("U2", SCI), unit("U3", SCI), unit("U4", SCI));

    private static TasteProfile profile(String user, List<ReviewObservation> reviews) {
        return TasteProfileBuilder.buildOne(user, reviews, UNITS);
    }

    @Test
    void oneSharedUnitIsShrunkHard() {
        TasteProfile a = profile("a", List.of(love("a", "U1")));
        TasteProfile b = profile("b", List.of(love("b", "U1")));
        // Perfect cosine over one unit, shrunk by 1 / (1 + 2)
        assertThat(UserSimilarity.collaborative(a, b)).isCloseTo(1.0 / 3, within(1e-9));
    }

    @Test
    void fourSharedUnitsAreTrustedMore() {
        TasteProfile a = profile("a", List.of(love("a", "U1"), love("a", "U2"), love("a", "U3"), love("a", "U4")));
        TasteProfile b = profile("b", List.of(love("b", "U1"), love("b", "U2"), love("b", "U3"), love("b", "U4")));
        assertThat(UserSimilarity.collaborative(a, b)).isCloseTo(4.0 / 6, within(1e-9));
        // Identical profiles also score a full profile term, so the blend is 0.85 * 2/3 + 0.15
        assertThat(UserSimilarity.similarity(a, b)).isCloseTo(0.85 * 4.0 / 6 + 0.15, within(1e-9));
    }

    @Test
    void oppositeTastesAreNegative() {
        TasteProfile a = profile("a", List.of(love("a", "U1"), love("a", "U2")));
        TasteProfile b = profile("b", List.of(hate("b", "U1"), hate("b", "U2")));
        assertThat(UserSimilarity.collaborative(a, b)).isCloseTo(-2.0 / 4, within(1e-9));
        assertThat(UserSimilarity.similarity(a, b)).isNegative();
    }

    @Test
    void noOverlapLeavesOnlyTheWeakProfileTerm() {
        TasteProfile a = profile("a", List.of(love("a", "U1")));
        TasteProfile b = profile("b", List.of(love("b", "U2")));
        assertThat(UserSimilarity.collaborative(a, b)).isZero();
        double sim = UserSimilarity.similarity(a, b);
        assertThat(sim).isGreaterThan(0).isLessThanOrEqualTo(RecommendationWeights.PROFILE_WEIGHT + 1e-9);
    }
}

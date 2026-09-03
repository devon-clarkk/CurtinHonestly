package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.dto.RecommendationItemDTO;
import com.curtinhonestly.backend.dto.RecommendationSimilarUnitsDTO;
import com.curtinhonestly.backend.dto.RecommendationsDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.BUS;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.SCI;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.hate;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.love;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.rated;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.unit;
import static com.curtinhonestly.backend.service.recommendation.RecommendationFixtures.units;
import static org.assertj.core.api.Assertions.assertThat;

class RecommendationEngineTest {

    private static final Map<String, UnitInfo> UNITS = units(
            unit("U1", SCI), unit("U2", SCI), unit("U3", SCI), unit("U4", SCI), unit("U5", SCI),
            unit("B1", BUS), unit("B2", BUS), unit("B3", BUS));

    private static RecommendationEngine engine(List<ReviewObservation> observations) {
        return new RecommendationEngine(RecommendationModel.build(observations, UNITS));
    }

    private static List<ReviewObservation> sharedFourLoved(String... users) {
        List<ReviewObservation> out = new ArrayList<>();
        for (String u : users) {
            out.add(love(u, "U1"));
            out.add(love(u, "U2"));
            out.add(love(u, "U3"));
            out.add(love(u, "U4"));
        }
        return out;
    }

    private static RecommendationItemDTO find(List<RecommendationItemDTO> items, String code) {
        return items.stream().filter(i -> i.unitCode().equals(code)).findFirst().orElse(null);
    }

    // ------------------------------------------------ product owner scenario

    @Test
    void fifthUnitLovedByATwinIsRecommended() {
        List<ReviewObservation> obs = sharedFourLoved("A", "B");
        obs.add(love("B", "U5"));

        RecommendationsDTO result = engine(obs).recommend("A", Set.of());

        assertThat(result.coldStart()).isFalse();
        assertThat(result.basedOnReviews()).isEqualTo(4);
        assertThat(result.neighbourCount()).isEqualTo(1);
        assertThat(result.avoid()).isEmpty();

        RecommendationItemDTO u5 = find(result.recommended(), "U5");
        assertThat(u5).isNotNull();
        assertThat(u5.matchScore()).isGreaterThanOrEqualTo(95);
        assertThat(u5.supportingStudents()).isEqualTo(1);
        // One neighbour at about 0.72 similarity gives 100 * (1 - e^-0.72), about 51 percent
        assertThat(u5.confidence()).isBetween(45, 60);
        assertThat(u5.reasons()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(u5.unitName()).isEqualTo("Unit U5");
        assertThat(u5.faculty()).isEqualTo("Science and Engineering");
    }

    @Test
    void fifthUnitHatedByATwinLandsInAvoid() {
        List<ReviewObservation> obs = sharedFourLoved("A", "B");
        obs.add(hate("B", "U5"));

        RecommendationsDTO result = engine(obs).recommend("A", Set.of());

        assertThat(result.coldStart()).isFalse();
        assertThat(find(result.recommended(), "U5")).isNull();
        RecommendationItemDTO u5 = find(result.avoid(), "U5");
        assertThat(u5).isNotNull();
        assertThat(u5.matchScore()).isLessThanOrEqualTo(5);
        assertThat(u5.reasons()).anyMatch(r -> r.contains("would not take this again"));
    }

    @Test
    void moreAgreeingNeighboursMeanHigherConfidence() {
        List<ReviewObservation> obs = sharedFourLoved("A", "B", "C", "D");
        obs.add(love("B", "U5"));
        obs.add(love("C", "U5"));
        obs.add(love("D", "U5"));

        RecommendationsDTO result = engine(obs).recommend("A", Set.of());

        assertThat(result.neighbourCount()).isEqualTo(3);
        RecommendationItemDTO u5 = find(result.recommended(), "U5");
        assertThat(u5).isNotNull();
        assertThat(u5.supportingStudents()).isEqualTo(3);
        assertThat(u5.confidence()).isGreaterThanOrEqualTo(80);
        assertThat(u5.reasons()).contains("All 3 similar students would take this again");
    }

    @Test
    void disagreementLowersConfidence() {
        List<ReviewObservation> agree = sharedFourLoved("A", "B", "C");
        agree.add(love("B", "U5"));
        agree.add(love("C", "U5"));
        int agreed = find(engine(agree).recommend("A", Set.of()).recommended(), "U5").confidence();

        List<ReviewObservation> split = sharedFourLoved("A", "B", "C");
        split.add(love("B", "U5"));
        split.add(rated("C", "U5", 3));
        RecommendationsDTO result = engine(split).recommend("A", Set.of());
        RecommendationItemDTO u5 = find(result.recommended(), "U5");
        assertThat(u5).isNotNull();
        assertThat(u5.confidence()).isLessThan(agreed);
        assertThat(u5.reasons()).contains("1 of 2 similar students would take this again");
    }

    @Test
    void reasonsOnlyNameUnitsTheTargetReviewed() {
        List<ReviewObservation> obs = sharedFourLoved("A", "B");
        obs.add(love("B", "U5"));
        obs.add(love("B", "B1"));
        obs.add(love("B", "B2"));

        RecommendationsDTO result = engine(obs).recommend("A", Set.of());

        Pattern code = Pattern.compile("\\b[UB]\\d\\b");
        Set<String> allowed = Set.of("U1", "U2", "U3", "U4");
        for (RecommendationItemDTO item : result.recommended()) {
            for (String reason : item.reasons()) {
                Matcher m = code.matcher(reason);
                while (m.find()) {
                    assertThat(allowed).contains(m.group());
                }
            }
        }
        assertThat(find(result.recommended(), "U5").reasons())
                .anyMatch(r -> r.startsWith("Popular with students who liked U1 and U2"));
    }

    @Test
    void reviewedAndCompletedUnitsAreNeverRecommended() {
        List<ReviewObservation> obs = sharedFourLoved("A", "B");
        obs.add(love("B", "U5"));
        obs.add(love("B", "B1"));

        RecommendationsDTO result = engine(obs).recommend("A", Set.of("u5"));

        assertThat(find(result.recommended(), "U5")).isNull();
        assertThat(find(result.recommended(), "U1")).isNull();
        assertThat(find(result.recommended(), "B1")).isNotNull();
    }

    // ------------------------------------------------------------ cold start

    @Test
    void oneReviewIsAColdStartScopedToThatFaculty() {
        List<ReviewObservation> obs = new ArrayList<>();
        obs.add(love("A", "U1"));
        // Popular units elsewhere in the catalogue
        for (String u : List.of("p", "q", "r", "s")) {
            obs.add(rated(u, "U2", 5));
            obs.add(rated(u, "B1", 5));
        }
        obs.add(rated("p", "U3", 4));
        obs.add(rated("t", "U5", 2));

        RecommendationsDTO result = engine(obs).recommend("A", Set.of());

        assertThat(result.coldStart()).isTrue();
        assertThat(result.message()).contains("at least two units").contains("faculties you study");
        assertThat(result.basedOnReviews()).isEqualTo(1);
        assertThat(result.neighbourCount()).isZero();
        assertThat(result.avoid()).isEmpty();
        List<String> codes = result.recommended().stream().map(RecommendationItemDTO::unitCode).toList();
        assertThat(codes).doesNotContain("U1", "B1");
        assertThat(codes.get(0)).isEqualTo("U2");
        assertThat(codes.indexOf("U3")).isLessThan(codes.indexOf("U5"));
        RecommendationItemDTO u2 = find(result.recommended(), "U2");
        // min(60, 20 + 5 * 4)
        assertThat(u2.confidence()).isEqualTo(40);
        assertThat(u2.supportingStudents()).isEqualTo(4);
        assertThat(u2.reasons()).anyMatch(r -> r.startsWith("Rated 5.0 out of 5 by 4 students"));
    }

    @Test
    void noReviewsAtAllFallsBackToGlobalList() {
        List<ReviewObservation> obs = new ArrayList<>();
        obs.add(rated("p", "U2", 5));
        obs.add(rated("p", "B1", 4));

        RecommendationsDTO result = engine(obs).recommend("nobody", Set.of());

        assertThat(result.coldStart()).isTrue();
        assertThat(result.basedOnReviews()).isZero();
        assertThat(result.message()).contains("on CurtinHonestly");
        assertThat(result.recommended()).extracting(RecommendationItemDTO::unitCode).containsExactly("U2", "B1");
    }

    @Test
    void completedUnitsScopeTheColdStartAndAreExcluded() {
        List<ReviewObservation> obs = new ArrayList<>();
        obs.add(rated("p", "B1", 5));
        obs.add(rated("p", "B2", 4));
        obs.add(rated("p", "U2", 5));

        RecommendationsDTO result = engine(obs).recommend("nobody", Set.of("B1"));

        assertThat(result.recommended()).extracting(RecommendationItemDTO::unitCode).containsExactly("B2");
    }

    @Test
    void enoughReviewsButNoNeighboursIsAColdStart() {
        List<ReviewObservation> obs = new ArrayList<>();
        obs.add(love("A", "U1"));
        obs.add(love("A", "U2"));
        obs.add(rated("p", "B1", 5));

        RecommendationsDTO result = engine(obs).recommend("A", Set.of());

        // p shares no units with A and their tag profiles do not overlap, so the
        // profile term alone stays under the neighbour threshold.
        assertThat(result.coldStart()).isTrue();
        assertThat(result.message()).startsWith("Personalised picks appear");
        assertThat(result.basedOnReviews()).isEqualTo(2);
    }

    // --------------------------------------------------------- unit to unit

    @Test
    void similarUnitsAreTheOnesCoReviewersAlsoLiked() {
        List<ReviewObservation> obs = new ArrayList<>();
        for (String u : List.of("a", "b", "c")) {
            obs.add(love(u, "U1"));
            obs.add(love(u, "U2"));
        }
        obs.add(love("a", "U3"));
        obs.add(love("b", "U3"));
        obs.add(hate("c", "U3"));
        // U4: only one co-reviewer, below the minimum
        obs.add(love("a", "U4"));
        // U5: co-reviewers disliked it
        obs.add(hate("a", "U5"));
        obs.add(hate("b", "U5"));

        RecommendationSimilarUnitsDTO result = engine(obs).similarUnits(UNITS.get("U1"));

        assertThat(result.basedOnCoReviews()).isTrue();
        assertThat(result.items().get(0).unitCode()).isEqualTo("U2");
        assertThat(result.items().get(0).sharedStudents()).isEqualTo(3);
        // cosine 1 over three shared reviewers, shrunk by 3 / (3 + 2)
        assertThat(result.items().get(0).matchScore()).isEqualTo(60);
        assertThat(result.items().get(1).unitCode()).isEqualTo("U3");
        assertThat(result.items()).extracting(i -> i.unitCode()).doesNotContain("U1", "U5");
        // Fewer than three co-review matches, so the list is topped up from the same faculty and level
        assertThat(result.items()).extracting(i -> i.unitCode()).contains("U4");
        assertThat(result.items().stream().filter(i -> i.unitCode().equals("U4")).findFirst().orElseThrow()
                .sharedStudents()).isZero();
    }

    @Test
    void unitWithNoReviewsFallsBackToCatalogueNeighbours() {
        List<ReviewObservation> obs = new ArrayList<>();
        obs.add(rated("a", "U2", 4));
        obs.add(rated("a", "U3", 2));
        obs.add(rated("a", "B1", 4));

        UnitInfo target = unit("U9", SCI, UnitLevel.UNDERGRADUATE, 4.0);
        RecommendationSimilarUnitsDTO result = engine(obs).similarUnits(target);

        assertThat(result.basedOnCoReviews()).isFalse();
        assertThat(result.items()).extracting(i -> i.unitCode()).containsExactly("U2", "U3");
        assertThat(result.items().get(0).matchScore()).isEqualTo(100);
        assertThat(result.items()).allMatch(i -> i.sharedStudents() == 0);
    }
}

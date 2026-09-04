package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.ReviewTag;
import com.curtinhonestly.backend.domain.UnitLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Shared builders for the pure recommender tests. */
final class RecommendationFixtures {

    private RecommendationFixtures() {
    }

    static final Faculty SCI = Faculty.SCIENCE_AND_ENGINEERING;
    static final Faculty BUS = Faculty.BUSINESS_AND_LAW;

    static Map<String, UnitInfo> units(UnitInfo... infos) {
        Map<String, UnitInfo> map = new HashMap<>();
        for (UnitInfo info : infos) {
            map.put(info.code(), info);
        }
        return map;
    }

    static UnitInfo unit(String code, Faculty faculty) {
        return new UnitInfo(code, "Unit " + code, faculty, UnitLevel.UNDERGRADUATE, 0);
    }

    static UnitInfo unit(String code, Faculty faculty, UnitLevel level, double averageRating) {
        return new UnitInfo(code, "Unit " + code, faculty, level, averageRating);
    }

    /** Five stars, good grade, would take again: affinity clamps to 1.0. */
    static ReviewObservation love(String user, String code) {
        return new ReviewObservation(user, code, 5, 80, 5, true, Set.of(ReviewTag.RECORDED_LECTURES));
    }

    /** One star, failed, would not take again: affinity clamps to -1.0. */
    static ReviewObservation hate(String user, String code) {
        return new ReviewObservation(user, code, 1, 40, 8, false, Set.of(ReviewTag.GROUP_WORK));
    }

    /** Four stars, no grade, would take again: affinity 0.8. */
    static ReviewObservation like(String user, String code) {
        return new ReviewObservation(user, code, 4, null, 5, true, Set.of(ReviewTag.RECORDED_LECTURES));
    }

    /** Three stars, no grade, would not take again: affinity -0.3. */
    static ReviewObservation meh(String user, String code) {
        return new ReviewObservation(user, code, 3, null, 5, false, Set.of());
    }

    static ReviewObservation rated(String user, String code, int rating) {
        return new ReviewObservation(user, code, rating, null, 5, rating >= 4, Set.of());
    }
}

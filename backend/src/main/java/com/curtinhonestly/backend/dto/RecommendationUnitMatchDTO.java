package com.curtinhonestly.backend.dto;

import java.util.List;

/**
 * Response of GET /recommendations/me/units/{code}: how well one unit fits the
 * signed-in student.
 *
 * @param state              MATCH when similar students have reviewed the unit;
 *                           REVIEWED when the student reviewed it themselves;
 *                           COLD_START when they have too few reviews for a
 *                           personalised score; NO_SIGNAL when they have enough
 *                           reviews but no similar student has reviewed this unit
 * @param matchScore         predicted affinity mapped to 0..100 (0 unless MATCH)
 * @param confidence         5..99 for MATCH, 0 otherwise
 * @param supportingStudents similar students whose reviews the score rests on
 * @param reasons            up to three short data-derived explanations (MATCH only)
 * @param basedOnReviews     the student's own review count
 */
public record RecommendationUnitMatchDTO(
        State state,
        int matchScore,
        int confidence,
        int supportingStudents,
        List<String> reasons,
        int basedOnReviews
) {
    public enum State { MATCH, REVIEWED, COLD_START, NO_SIGNAL }

    public static RecommendationUnitMatchDTO of(State state, int basedOnReviews) {
        return new RecommendationUnitMatchDTO(state, 0, 0, 0, List.of(), basedOnReviews);
    }
}

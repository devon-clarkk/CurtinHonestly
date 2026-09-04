package com.curtinhonestly.backend.dto;

import java.util.List;

/**
 * One recommended (or advised-against) unit.
 *
 * @param matchScore         predicted affinity mapped to 0..100
 * @param confidence         5..99, how much supporting evidence there is
 * @param supportingStudents number of similar students the item is based on
 *                           (review count for cold-start fallback items)
 * @param reasons            up to three short data-derived explanations
 */
public record RecommendationItemDTO(
        String unitCode,
        String unitName,
        String faculty,
        String level,
        int matchScore,
        int confidence,
        int supportingStudents,
        List<String> reasons
) {}

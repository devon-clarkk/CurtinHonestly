package com.curtinhonestly.backend.dto;

import java.util.List;

/**
 * Response of GET /recommendations/me.
 *
 * @param coldStart      true when the user has too few reviews or no similar
 *                       students yet; recommended then holds a rating-based
 *                       fallback list and avoid is empty
 * @param message        explanatory copy for the cold-start state, null otherwise
 * @param basedOnReviews number of the user's own reviews the result rests on
 * @param neighbourCount number of similar students consulted
 */
public record RecommendationsDTO(
        boolean coldStart,
        String message,
        int basedOnReviews,
        int neighbourCount,
        List<RecommendationItemDTO> recommended,
        List<RecommendationItemDTO> avoid
) {}

package com.curtinhonestly.backend.dto;

import java.util.List;

/**
 * Response of GET /units/{code}/similar.
 *
 * @param basedOnCoReviews true when at least one item comes from students who
 *                         reviewed both units; false when the whole list is the
 *                         same-faculty, same-level catalogue fallback
 */
public record RecommendationSimilarUnitsDTO(List<RecommendationSimilarUnitDTO> items, boolean basedOnCoReviews) {}

package com.curtinhonestly.backend.dto;

/**
 * One unit that students who liked the requested unit also rated well.
 *
 * @param matchScore     0..100
 * @param sharedStudents co-reviewers behind the score; 0 for catalogue fallback items
 */
public record RecommendationSimilarUnitDTO(
        String unitCode,
        String unitName,
        String faculty,
        String level,
        int matchScore,
        int sharedStudents
) {}

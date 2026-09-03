package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.ReviewTag;

import java.util.Set;

/**
 * The slice of a review the recommender reads. Decoupled from the JPA entity so
 * the algorithm can be exercised without a database or Spring.
 *
 * @param userId null for anonymised reviews (author deleted their account). Such
 *               reviews still count towards unit statistics but build no taste
 *               profile.
 */
public record ReviewObservation(
        String userId,
        String unitCode,
        int rating,
        Integer finalGrade,
        int workload,
        boolean wouldTakeAgain,
        Set<ReviewTag> tags
) {
    public ReviewObservation {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}

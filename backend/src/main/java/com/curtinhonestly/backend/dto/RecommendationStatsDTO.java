package com.curtinhonestly.backend.dto;

import java.time.Instant;

/**
 * Response of GET /admin/recommendations/stats: the shape of the in-memory
 * recommendation model currently serving requests.
 *
 * @param builtAt                when the snapshot was built; the model is rebuilt after
 *                               review changes and at most every ten minutes
 * @param reviewCount            reviews in the model, attributed and anonymised
 * @param userCount              students with at least one attributed review
 * @param unitCount              units with at least one review
 * @param usersWithNeighbours    students who get personalised picks: enough
 *                               reviews and at least one similar student
 * @param coldStartUsers         students with a review but under the personalisation
 *                               minimum, or with no similar student
 * @param meanNeighboursPerUser  mean neighbourhood size (capped at the neighbour
 *                               limit) over students with neighbours
 * @param itemPairsWithCoReviews pairs of units reviewed by at least one common student
 */
public record RecommendationStatsDTO(
        Instant builtAt,
        int reviewCount,
        int userCount,
        int unitCount,
        int usersWithNeighbours,
        int coldStartUsers,
        double meanNeighboursPerUser,
        int itemPairsWithCoReviews
) {}

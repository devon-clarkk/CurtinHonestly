package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.ReviewerProfileDTO;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.util.ReviewerRank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Computes {@link ReviewerRank}s from the database. One aggregate query per
 * call, never one per reviewer: a unit page with forty reviews asks for forty
 * ranks in a single round trip through {@link #ranksFor(Collection)}.
 *
 * Likes received is the sum of {@code Review.likeCount}, which
 * {@link ReviewLikeService} keeps in step with the review_likes table, so the
 * count here is the same number shown on each review card.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewerRankService {

    private final ReviewRepo reviewRepo;
    private final UserRepo userRepo;

    /** Rank for every distinct, still-attached author in the given reviews, keyed by user id. */
    public Map<String, ReviewerRank> ranksForAuthorsOf(Collection<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return Map.of();
        }
        Set<String> userIds = new LinkedHashSet<>();
        for (Review review : reviews) {
            if (review.getUser() != null && review.getUser().getId() != null) {
                userIds.add(review.getUser().getId());
            }
        }
        return ranksFor(userIds);
    }

    /**
     * Ranks for a set of user ids. Every requested id is present in the result;
     * a user with no reviews maps to {@link ReviewerRank#NONE}.
     */
    public Map<String, ReviewerRank> ranksFor(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String id : userIds) {
            if (id != null && !id.isBlank()) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<String, ReviewerRank> ranks = new HashMap<>();
        for (ReviewRepo.ReviewerStats stats : reviewRepo.aggregateReviewerStats(ids)) {
            if (stats.getUserId() == null) {
                continue;
            }
            long reviewCount = stats.getReviewCount() == null ? 0 : stats.getReviewCount();
            long likesReceived = stats.getLikesReceived() == null ? 0 : stats.getLikesReceived();
            ranks.put(stats.getUserId(), ReviewerRank.of(reviewCount, likesReceived));
        }
        for (String id : ids) {
            ranks.putIfAbsent(id, ReviewerRank.NONE);
        }
        return ranks;
    }

    public ReviewerRank rankFor(String userId) {
        if (userId == null || userId.isBlank()) {
            return ReviewerRank.NONE;
        }
        return ranksFor(Set.of(userId)).getOrDefault(userId, ReviewerRank.NONE);
    }

    /** The signed-in user's own profile. Requires an authenticated request. */
    public ReviewerProfileDTO profileForCurrentUser() {
        String email = Objects.requireNonNull(
                SecurityContextHolder.getContext().getAuthentication(), "No authenticated user").getName();
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
        return ReviewerProfileDTO.from(rankFor(user.getId()));
    }
}

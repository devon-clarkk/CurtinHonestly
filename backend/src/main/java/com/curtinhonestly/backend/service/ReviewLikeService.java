package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.ReviewLike;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.ReviewDTO;
import com.curtinhonestly.backend.dto.ReviewLikeResponseDTO;
import com.curtinhonestly.backend.repo.ReviewLikeRepo;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(rollbackOn = Exception.class)
@RequiredArgsConstructor
public class ReviewLikeService {

    private final ReviewLikeRepo reviewLikeRepo;
    private final ReviewRepo reviewRepo;
    private final UserRepo userRepo;
    private final CampaignService campaignService;

    public ReviewLikeResponseDTO likeReview(String reviewId) {
        User user = currentUser();
        Review review = reviewRepo.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));

        if (review.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You can't like your own review.");
        }

        if (reviewLikeRepo.existsByUser_IdAndReview_Id(user.getId(), review.getId())) {
            return new ReviewLikeResponseDTO(review.getId(), review.getLikeCount(), true);
        }

        ReviewLike like = new ReviewLike();
        like.setUser(user);
        like.setReview(review);
        like.setCreatedAt(Instant.now());

        try {
            reviewLikeRepo.saveAndFlush(like);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent duplicate like — treat as already liked.
            Review refreshed = reviewRepo.findById(reviewId).orElse(review);
            return new ReviewLikeResponseDTO(refreshed.getId(), refreshed.getLikeCount(), true);
        }

        review.setLikeCount(review.getLikeCount() + 1);
        Review savedReview = reviewRepo.save(review);

        // Likes can unlock campaign entries for the liker (minLikesGiven) and
        // for the review author (minLikesReceived) once thresholds are met.
        campaignService.reconcileCampaignEntries(user);
        campaignService.reconcileCampaignEntries(savedReview.getUser());

        log.info("User {} liked review {}", user.getId(), reviewId);
        return new ReviewLikeResponseDTO(savedReview.getId(), savedReview.getLikeCount(), true);
    }

    public ReviewLikeResponseDTO unlikeReview(String reviewId) {
        User user = currentUser();
        Review review = reviewRepo.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));

        Optional<ReviewLike> existing = reviewLikeRepo.findByUser_IdAndReview_Id(user.getId(), review.getId());
        if (existing.isEmpty()) {
            return new ReviewLikeResponseDTO(review.getId(), review.getLikeCount(), false);
        }

        reviewLikeRepo.delete(existing.get());
        review.setLikeCount(Math.max(0, review.getLikeCount() - 1));
        Review savedReview = reviewRepo.save(review);

        // Unlike never revokes earned tokens (same policy as review delete), but
        // progress counters for remaining awards should reflect the new state.
        campaignService.reconcileCampaignEntries(user);
        campaignService.reconcileCampaignEntries(savedReview.getUser());

        log.info("User {} unliked review {}", user.getId(), reviewId);
        return new ReviewLikeResponseDTO(savedReview.getId(), savedReview.getLikeCount(), false);
    }

    public void enrichReviewsWithCurrentUserLikes(List<ReviewDTO> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return;
        }

        Optional<String> userId = currentUserIdIfAuthenticated();
        if (userId.isEmpty()) {
            return;
        }

        Set<String> reviewIds = reviews.stream()
                .map(ReviewDTO::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (reviewIds.isEmpty()) {
            return;
        }

        Set<String> likedIds = likedReviewIds(userId.get(), reviewIds);
        for (ReviewDTO review : reviews) {
            if (review.getId() != null && likedIds.contains(review.getId())) {
                review.setLikedByCurrentUser(true);
            }
        }
    }

    public Set<String> likedReviewIds(String userId, Collection<String> reviewIds) {
        return reviewLikeRepo.findByUser_IdAndReview_IdIn(userId, reviewIds).stream()
                .map(like -> like.getReview().getId())
                .collect(Collectors.toSet());
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    private Optional<String> currentUserIdIfAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        return userRepo.findByEmail(name).map(User::getId);
    }
}

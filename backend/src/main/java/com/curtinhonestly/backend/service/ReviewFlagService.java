package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.ReviewFlag;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.FlaggedReviewDTO;
import com.curtinhonestly.backend.repo.ReviewFlagRepo;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(rollbackOn = Exception.class)
public class ReviewFlagService {

    private final ReviewFlagRepo flagRepo;
    private final ReviewRepo reviewRepo;
    private final UserRepo userRepo;

    /**
     * Idempotent: flagging a review you've already flagged is a no-op rather
     * than an error, so the frontend doesn't need special-case handling.
     */
    public void flagReview(String reviewId, String reason) {
        Review review = reviewRepo.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));
        User user = currentUser();

        if (flagRepo.existsByUser_IdAndReview_Id(user.getId(), reviewId)) {
            return;
        }

        ReviewFlag flag = new ReviewFlag();
        flag.setUser(user);
        flag.setReview(review);
        flag.setReason(reason != null && !reason.isBlank() ? reason.trim() : null);
        flagRepo.save(flag);
        log.info("User {} flagged review {}", user.getId(), reviewId);
    }

    public List<FlaggedReviewDTO> getFlaggedReviews() {
        return flagRepo.findDistinctFlaggedReviewIdsOrderByFlagCountDesc().stream()
                .map(this::toFlaggedReviewDTO)
                .filter(Objects::nonNull)
                .toList();
    }

    public void dismissFlags(String reviewId) {
        flagRepo.deleteByReview_Id(reviewId);
        log.info("Flags dismissed for review {}", reviewId);
    }

    private FlaggedReviewDTO toFlaggedReviewDTO(String reviewId) {
        return reviewRepo.findById(reviewId)
                .map(review -> new FlaggedReviewDTO(
                        review.getId(),
                        review.getUnit().getCode(),
                        review.getReviewText(),
                        flagRepo.countByReview_Id(reviewId)))
                .orElse(null);
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }
}

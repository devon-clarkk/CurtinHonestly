package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewSecurityService {

    private final ReviewService reviewService;

    // The @PreAuthorize SpEL passes `authentication`, i.e. the Authentication
    // token itself — which is never a UserDetails. Read the username off it via
    // getName() (for a UserDetails principal that is the username/email). The old
    // `instanceof UserDetails` check silently denied every owner, so no non-admin
    // could edit or delete their own review.
    public boolean isReviewOwner(String reviewId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        try {
            String username = authentication.getName();
            Review review = reviewService.getReviewById(reviewId);
            // Anonymized reviews (author deleted their account) have no
            // owner — no one can claim ownership of them.
            return review.getUser() != null && review.getUser().getEmail().equals(username);
        } catch (Exception e) {
            // If review not found or any other error, deny access
            return false;
        }
    }
}



package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewSecurityService {

    private final ReviewService reviewService;

    public boolean isReviewOwner(String reviewId, Object principal) {
        try {
            if (principal instanceof UserDetails) {
                String username = ((UserDetails) principal).getUsername();
                Review review = reviewService.getReviewById(reviewId);
                return review.getUser().getEmail().equals(username);
            }
        } catch (Exception e) {
            // If review not found or any other error, deny access
            return false;
        }
        return false;
    }
}



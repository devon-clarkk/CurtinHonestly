package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verifies the ownership check behind @PreAuthorize(IS_ADMIN_OR_OWNER) for review
 * edit/delete. Guards both directions of the historical bug: it must allow the real
 * owner (the old instanceof-UserDetails check denied everyone) AND must never allow a
 * different authenticated user (the fix must not over-correct into letting the wrong
 * user edit). The admin branch is the SpEL's own hasRole('ADMIN'), not this method.
 */
@ExtendWith(MockitoExtension.class)
class ReviewSecurityServiceTest {

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private ReviewSecurityService securityService;

    private Authentication authFor(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of());
    }

    private Review reviewOwnedBy(String ownerEmail) {
        User owner = new User();
        owner.setId("user-1");
        owner.setEmail(ownerEmail);
        Review review = new Review();
        review.setId("review-1");
        review.setUser(owner);
        return review;
    }

    @Test
    void ownerCanEditTheirOwnReview() {
        when(reviewService.getReviewById("review-1")).thenReturn(reviewOwnedBy("alice@student.curtin.edu.au"));

        assertThat(securityService.isReviewOwner("review-1", authFor("alice@student.curtin.edu.au"))).isTrue();
    }

    @Test
    void differentAuthenticatedUserCannotEditSomeoneElsesReview() {
        when(reviewService.getReviewById("review-1")).thenReturn(reviewOwnedBy("alice@student.curtin.edu.au"));

        assertThat(securityService.isReviewOwner("review-1", authFor("mallory@student.curtin.edu.au"))).isFalse();
    }

    @Test
    void anonymizedReviewHasNoOwner() {
        Review anonymized = new Review();
        anonymized.setId("review-1");
        anonymized.setUser(null); // author deleted their account
        when(reviewService.getReviewById("review-1")).thenReturn(anonymized);

        assertThat(securityService.isReviewOwner("review-1", authFor("alice@student.curtin.edu.au"))).isFalse();
    }

    @Test
    void unauthenticatedRequestIsDenied() {
        assertThat(securityService.isReviewOwner("review-1", null)).isFalse();
    }

    @Test
    void missingReviewIsDenied() {
        lenient().when(reviewService.getReviewById("nope"))
                .thenThrow(new RuntimeException("Review not found"));

        assertThat(securityService.isReviewOwner("nope", authFor("alice@student.curtin.edu.au"))).isFalse();
    }
}

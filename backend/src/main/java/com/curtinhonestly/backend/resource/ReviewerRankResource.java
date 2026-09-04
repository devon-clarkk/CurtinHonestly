package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.ReviewerProfileDTO;
import com.curtinhonestly.backend.security.SecurityConstants;
import com.curtinhonestly.backend.service.ReviewerRankService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in student's reviewer standing.
 *
 * Lives outside {@code /reviews} on purpose: SecurityConfig grants
 * {@code GET /reviews/**} to admins only, with {@code GET /reviews/me} as the
 * single exact-path exception, so a sibling such as {@code /reviews/me/profile}
 * would be refused for ordinary users before this controller ran. This path
 * matches no explicit rule and falls through to {@code anyRequest().authenticated()};
 * the role check on the method is what gates it to signed-in students.
 */
@RestController
@RequestMapping("/reviewer-rank")
@RequiredArgsConstructor
public class ReviewerRankResource {

    private final ReviewerRankService reviewerRankService;

    @GetMapping("/me")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<ReviewerProfileDTO> getMyReviewerProfile() {
        return ResponseEntity.ok(reviewerRankService.profileForCurrentUser());
    }
}

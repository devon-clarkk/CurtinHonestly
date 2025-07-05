package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.service.ReviewService;
import com.curtinhonestly.backend.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewResource {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<List<Review>> getAllReviews() {
        return ResponseEntity.ok().body(reviewService.getAllReviews());
    }

    @PostMapping
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<Review> createReview(@RequestBody Review review) {
        return ResponseEntity.created(URI.create("/reviews/" + review.getId()))
                .body(reviewService.createReview(review));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityConstants.IS_ADMIN_OR_OWNER)
    public ResponseEntity<?> deleteReview(@PathVariable String id) {
        reviewService.deleteReviewById(id);
        return ResponseEntity.noContent().build();
    }
}

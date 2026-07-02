package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.ReviewDTO;
import com.curtinhonestly.backend.mapper.ReviewMapper;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.service.ReviewService;
import com.curtinhonestly.backend.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
@Slf4j
public class ReviewResource {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<List<ReviewDTO>> getAllReviews() {
        return ResponseEntity.ok(ReviewMapper.mapToDTOs(reviewService.getAllReviews()));
    }

    @PostMapping
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<?> createReview(@RequestBody Review review) {
        try {
            Review savedReview = reviewService.createReview(review);
            return ResponseEntity.created(URI.create("/reviews/" + savedReview.getId()))
                    .body(ReviewMapper.mapToDTO(savedReview));
        } catch (Exception e) {
            log.error("Error creating review: {}", e.getMessage(), e);
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"Failed to create review: " + message + "\"}");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityConstants.IS_ADMIN_OR_OWNER)
    public ResponseEntity<?> deleteReview(@PathVariable String id) {
        reviewService.deleteReviewById(id);
        return ResponseEntity.noContent().build();
    }
}

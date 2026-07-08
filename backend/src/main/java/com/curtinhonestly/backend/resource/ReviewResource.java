package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.MyReviewDTO;
import com.curtinhonestly.backend.dto.ReviewCreateRequest;
import com.curtinhonestly.backend.dto.ReviewDTO;
import com.curtinhonestly.backend.mapper.ReviewMapper;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.service.ReviewService;
import com.curtinhonestly.backend.security.SecurityConstants;
import jakarta.validation.Valid;
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

    @GetMapping("/me")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<List<MyReviewDTO>> getMyReviews() {
        return ResponseEntity.ok(ReviewMapper.mapToMyReviewDTOs(reviewService.getReviewsForCurrentUser()));
    }

    @GetMapping
    public ResponseEntity<List<ReviewDTO>> getAllReviews() {
        return ResponseEntity.ok(ReviewMapper.mapToDTOs(reviewService.getAllReviews()));
    }

    @PostMapping
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<?> createReview(@Valid @RequestBody ReviewCreateRequest request) {
        Review savedReview = reviewService.createReview(request);
        return ResponseEntity.created(URI.create("/reviews/" + savedReview.getId()))
                .body(ReviewMapper.mapToDTO(savedReview));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityConstants.IS_ADMIN_OR_OWNER)
    public ResponseEntity<?> deleteReview(@PathVariable String id) {
        reviewService.deleteReviewById(id);
        return ResponseEntity.noContent().build();
    }
}

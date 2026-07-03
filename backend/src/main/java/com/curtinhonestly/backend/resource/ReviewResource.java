package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.MyReviewDTO;
import com.curtinhonestly.backend.dto.CreateReviewResponseDTO;
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
    public ResponseEntity<?> createReview(@RequestBody Review review) {
        try {
            ReviewService.ReviewCreationResult result = reviewService.createReviewWithCampaignEntry(review);
            Review savedReview = result.review();
            ReviewDTO reviewDto = ReviewMapper.mapToDTO(savedReview);

            String entryToken = null;
            String campaignName = null;
            if (result.campaignEntry().isPresent()) {
                var entry = result.campaignEntry().get();
                entryToken = entry.getEntryToken();
                campaignName = entry.getCampaign().getName();
            }

            CreateReviewResponseDTO response = new CreateReviewResponseDTO(reviewDto, entryToken, campaignName);
            return ResponseEntity.created(URI.create("/reviews/" + savedReview.getId())).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Review validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
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

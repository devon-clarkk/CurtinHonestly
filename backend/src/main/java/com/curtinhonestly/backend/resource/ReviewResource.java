package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.CreateReviewResponseDTO;
import com.curtinhonestly.backend.dto.FlagReviewRequest;
import com.curtinhonestly.backend.dto.MyReviewDTO;
import com.curtinhonestly.backend.dto.ReviewCreateRequest;
import com.curtinhonestly.backend.dto.ReviewDTO;
import com.curtinhonestly.backend.dto.ReviewLikeResponseDTO;
import com.curtinhonestly.backend.dto.ReviewUpdateRequest;
import com.curtinhonestly.backend.mapper.ReviewMapper;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.service.ReviewFlagService;
import com.curtinhonestly.backend.service.ReviewLikeService;
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
    private final ReviewLikeService reviewLikeService;
    private final ReviewFlagService reviewFlagService;

    @GetMapping("/me")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<List<MyReviewDTO>> getMyReviews() {
        return ResponseEntity.ok(ReviewMapper.mapToMyReviewDTOs(reviewService.getReviewsForCurrentUser()));
    }

    @GetMapping
    @PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
    public ResponseEntity<List<ReviewDTO>> getAllReviews() {
        return ResponseEntity.ok(ReviewMapper.mapToDTOs(reviewService.getAllReviews()));
    }

    @PostMapping
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<?> createReview(@Valid @RequestBody ReviewCreateRequest request) {
        ReviewService.ReviewCreationResult result = reviewService.createReviewWithCampaignEntry(request);
        Review savedReview = result.review();
        ReviewDTO reviewDto = ReviewMapper.mapToDTO(savedReview);

        String entryToken = null;
        String campaignName = null;
        if (!result.newEntries().isEmpty()) {
            var first = result.newEntries().get(0);
            entryToken = first.getEntryToken();
            campaignName = first.getCampaign().getName();
        }

        CreateReviewResponseDTO response = new CreateReviewResponseDTO(
                reviewDto, entryToken, campaignName, result.newEntries().size());
        return ResponseEntity.created(URI.create("/reviews/" + savedReview.getId())).body(response);
    }

    @PostMapping("/{id}/likes")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<ReviewLikeResponseDTO> likeReview(@PathVariable String id) {
        return ResponseEntity.ok(reviewLikeService.likeReview(id));
    }

    @DeleteMapping("/{id}/likes")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<ReviewLikeResponseDTO> unlikeReview(@PathVariable String id) {
        return ResponseEntity.ok(reviewLikeService.unlikeReview(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityConstants.IS_ADMIN_OR_OWNER)
    public ResponseEntity<ReviewDTO> updateReview(@PathVariable String id, @Valid @RequestBody ReviewUpdateRequest request) {
        Review updated = reviewService.updateReview(id, request);
        return ResponseEntity.ok(ReviewMapper.mapToDTO(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityConstants.IS_ADMIN_OR_OWNER)
    public ResponseEntity<?> deleteReview(@PathVariable String id) {
        reviewService.deleteReviewById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/flags")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<Void> flagReview(@PathVariable String id, @Valid @RequestBody(required = false) FlagReviewRequest request) {
        reviewFlagService.flagReview(id, request != null ? request.reason() : null);
        return ResponseEntity.noContent().build();
    }
}

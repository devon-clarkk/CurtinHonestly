package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.CampaignEntry;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.ReviewCreateRequest;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@Transactional(rollbackOn = Exception.class)
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepo reviewRepo;
    private final UnitService unitService;
    private final UserRepo userRepo;
    private final UnitRepo unitRepo;
    private final ProfanityFilterService profanityFilterService;
    private final UnitAggregateService unitAggregateService;
    private final CampaignService campaignService;

    public List<Review> getReviewsByUnitCode(String unitCode) {
        Unit unit = unitService.getUnitByCode(unitCode);
        return reviewRepo.findByUnit_Id(unit.getId());
    }

    public Page<Review> getPageOfReviews(int page, int size) {
        return reviewRepo.findAll(PageRequest.of(page, size));
    }

    public Review getReviewById(String id) throws RuntimeException {
        return reviewRepo.findById(id).orElseThrow(() -> new RuntimeException("Review not found"));
    }

    public Review createReview(ReviewCreateRequest request) {
        // Get authenticated user's email
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        // Load User entity
        User user = userRepo.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // Check for profanity
        if (profanityFilterService.containsProfanity(request.reviewText())) {
            throw new IllegalArgumentException("Review contains inappropriate language. Please keep it professional.");
        }

        // Look up full Unit entity by code
        Unit unit = unitRepo.findByCode(request.unitCode())
                .orElseThrow(() -> new RuntimeException("Unit not found with code: " + request.unitCode()));

        // Build the Review server-side from only the user-settable fields.
        // createdAt and id are never taken from client input.
        Review review = new Review();
        review.setRating(request.rating());
        review.setFinalGrade(request.finalGrade());
        review.setReviewText(request.reviewText());
        review.setSemesterTaken(request.semesterTaken());
        review.setProfessor(request.professor());
        review.setWorkload(request.workload());
        review.setHasExam(request.hasExam());
        review.setWouldTakeAgain(request.wouldTakeAgain());
        review.setCreatedAt(Instant.now());
        review.setUnit(unit);
        review.setUser(user);

        log.info("Review added by user: {}", username);

        Review saved = reviewRepo.save(review);
        unitAggregateService.recalculateForUnit(unit.getId());
        return saved;
    }

    public ReviewCreationResult createReviewWithCampaignEntry(ReviewCreateRequest request) {
        Review savedReview = createReview(request);
        Optional<CampaignEntry> entry = campaignService.tryCreateEntryForReview(savedReview.getUser(), savedReview);
        return new ReviewCreationResult(savedReview, entry);
    }

    public record ReviewCreationResult(Review review, Optional<CampaignEntry> campaignEntry) {}

    public void deleteReview(Review review) {
        String unitId = review.getUnit().getId();
        log.info("Review deleted");
        reviewRepo.delete(review);
        unitAggregateService.recalculateForUnit(unitId);
    }

    public void deleteReviewById(String id) {
        Review review = getReviewById(id);
        deleteReview(review);
    }

    public List<Review> getAllReviews() {
        return reviewRepo.findAll();
    }

    public List<Review> getReviewsForCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
        return reviewRepo.findByUser_IdOrderByCreatedAtDesc(user.getId());
    }
}

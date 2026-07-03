package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.CampaignEntry;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.CampaignProgressDTO;
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

    public Optional<Review> getReviewForCurrentUserAndUnit(String unitCode) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
        Unit unit = unitRepo.findByCode(unitCode)
                .orElseThrow(() -> new RuntimeException("Unit not found with code: " + unitCode));
        return reviewRepo.findByUser_IdAndUnit_Id(user.getId(), unit.getId());
    }

    public Review createReview(Review review) {
        User user = getCurrentUser();
        validateReviewFields(review);

        if (review.getUnit() == null || review.getUnit().getCode() == null) {
            throw new IllegalArgumentException("Unit code must be provided in review");
        }

        Unit unit = unitRepo.findByCode(review.getUnit().getCode())
                .orElseThrow(() -> new RuntimeException("Unit not found with code: " + review.getUnit().getCode()));

        if (reviewRepo.existsByUser_IdAndUnit_Id(user.getId(), unit.getId())) {
            throw new IllegalArgumentException("You have already reviewed this unit. Edit your existing review instead.");
        }

        review.setUnit(unit);
        review.setUser(user);

        log.info("Review added by user: {}", user.getEmail());
        return reviewRepo.save(review);
    }

    public Review updateReview(String reviewId, Review updates) {
        User user = getCurrentUser();
        Review existing = getReviewById(reviewId);

        if (!existing.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You can only edit your own reviews.");
        }

        validateReviewFields(updates);

        existing.setRating(updates.getRating());
        existing.setFinalGrade(updates.getFinalGrade());
        existing.setReviewText(updates.getReviewText());
        existing.setSemesterTaken(updates.getSemesterTaken());
        existing.setProfessor(updates.getProfessor());
        existing.setWorkload(updates.getWorkload());
        existing.setHasExam(updates.isHasExam());
        existing.setWouldTakeAgain(updates.isWouldTakeAgain());

        log.info("Review updated by user: {}", user.getEmail());
        return reviewRepo.save(existing);
    }

    public ReviewCreationResult createReviewWithCampaignEntry(Review review) {
        Review savedReview = createReview(review);
        CampaignService.CampaignAwardResult award = campaignService.tryAwardCampaignEntries(savedReview.getUser(), savedReview);
        return new ReviewCreationResult(savedReview, award.newEntry(), award.progress());
    }

    public ReviewCreationResult updateReviewWithCampaignEntry(String reviewId, Review updates) {
        Review savedReview = updateReview(reviewId, updates);
        CampaignService.CampaignAwardResult award = campaignService.tryAwardCampaignEntries(savedReview.getUser(), savedReview);
        return new ReviewCreationResult(savedReview, award.newEntry(), award.progress());
    }

    public record ReviewCreationResult(
            Review review,
            Optional<CampaignEntry> campaignEntry,
            CampaignProgressDTO campaignProgress
    ) {}

    public void deleteReview(Review review) {
        log.info("Review deleted");
        reviewRepo.delete(review);
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

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    private void validateReviewFields(Review review) {
        if (review.getRating() < 0 || review.getRating() > 5) {
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        }

        if (review.getFinalGrade() != null && (review.getFinalGrade() < 0 || review.getFinalGrade() > 100)) {
            throw new IllegalArgumentException("Final grade must be between 0 and 100%");
        }

        if (profanityFilterService.containsProfanity(review.getReviewText())) {
            throw new IllegalArgumentException("Review contains inappropriate language. Please keep it professional.");
        }
    }
}

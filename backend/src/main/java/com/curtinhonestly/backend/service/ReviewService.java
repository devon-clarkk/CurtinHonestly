package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
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

    public Review createReview(Review review) {
        // Get authenticated user's email
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        // Load User entity
        User user = userRepo.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // Validate rating scale (0-5)
        if (review.getRating() < 0 || review.getRating() > 5) {
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        }

        // Validate grade scale (0-100)
        if (review.getFinalGrade() != null && (review.getFinalGrade() < 0 || review.getFinalGrade() > 100)) {
            throw new IllegalArgumentException("Final grade must be between 0 and 100%");
        }

        // Check for profanity
        if (profanityFilterService.containsProfanity(review.getReviewText())) {
            throw new IllegalArgumentException("Review contains inappropriate language. Please keep it professional.");
        }

        // Extract unit code from incoming Review object
        if (review.getUnit() == null || review.getUnit().getCode() == null) {
            throw new IllegalArgumentException("Unit code must be provided in review");
        }
        String unitCode = review.getUnit().getCode();

        // Look up full Unit entity by code
        Unit unit = unitRepo.findByCode(unitCode)
                .orElseThrow(() -> new RuntimeException("Unit not found with code: " + unitCode));

        // Set the full Unit and User entities on the review before saving
        review.setUnit(unit);
        review.setUser(user);

        log.info("Review added by user: {}", username);

        Review saved = reviewRepo.save(review);
        unitAggregateService.recalculateForUnit(unit.getId());
        return saved;
    }

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

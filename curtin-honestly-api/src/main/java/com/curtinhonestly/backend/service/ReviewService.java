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

    public List<Review> getReviewsByUnitCode(String unitCode) {
        Unit unit = unitService.getUnitByCode(unitCode);
        return reviewRepo.findByUnit_Id(unit.getId());
    }

    public Page<Review> getPageOfReviews(int page, int size) {
        return reviewRepo.findAll(PageRequest.of(page, size, Sort.by("date")));
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

        return reviewRepo.save(review);
    }

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
}

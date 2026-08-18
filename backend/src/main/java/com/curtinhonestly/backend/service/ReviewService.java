package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.AcademicTerm;
import com.curtinhonestly.backend.domain.CampaignEntry;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.CampaignProgressDTO;
import com.curtinhonestly.backend.dto.ReviewCreateRequest;
import com.curtinhonestly.backend.dto.ReviewUpdateRequest;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.util.ReviewRanking;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        // Same ranking the unit page gets from UnitMapper, so the two endpoints
        // cannot disagree about what order a unit's reviews are in.
        return ReviewRanking.rank(reviewRepo.findByUnit_IdOrderByCreatedAtDesc(unit.getId()));
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

        if (reviewRepo.existsByUser_IdAndUnit_Id(user.getId(), unit.getId())) {
            throw new IllegalArgumentException("You've already reviewed this unit. Delete your existing review from My Reviews before posting a new one.");
        }

        // Build the Review server-side from only the user-settable fields.
        // createdAt and id are never taken from client input.
        Review review = new Review();
        review.setRating(request.rating());
        review.setFinalGrade(request.finalGrade());
        review.setReviewText(request.reviewText());
        applyTerm(review, request.termType(), request.termYear());
        review.setProfessor(request.professor());
        review.setWorkload(request.workload());
        review.setHasExam(request.hasExam());
        review.setWouldTakeAgain(request.wouldTakeAgain());
        // Wrap in a mutable set: an immutable Set.of() (or Jackson's immutable empty
        // set) blows up Hibernate's merge with UnsupportedOperationException when it
        // clears the collection on update.
        review.setTags(new HashSet<>(request.tags() != null ? request.tags() : Set.of()));
        review.setCreatedAt(Instant.now());
        review.setUnit(unit);
        review.setUser(user);

        log.info("Review added by user: {}", username);

        Review saved = reviewRepo.save(review);
        unitAggregateService.recalculateForUnit(unit.getId());
        return saved;
    }

    /**
     * Writes the term a review refers to, enforcing the one invariant the pair has:
     * EARLIER_UNSPECIFIED is an open-ended bucket, so it never carries a year. A
     * client sending one anyway would otherwise produce rows that look like a real
     * dated term to any aggregate query.
     *
     * The legacy semesterTaken column is deliberately left untouched. It holds
     * backfilled history only and is dropped once nothing reads it.
     */
    private void applyTerm(Review review, AcademicTerm termType, Integer termYear) {
        review.setTermType(termType);
        review.setTermYear(termType == AcademicTerm.EARLIER_UNSPECIFIED ? null : termYear);
    }

    /**
     * Update an existing review in place. Unit, author, and createdAt are never
     * touched — only the reviewable fields the user is allowed to change.
     * Aggregate recalculation is required here (unlike anonymize-on-delete)
     * since rating/workload/grade/wouldTakeAgain can all change.
     */
    public Review updateReview(String id, ReviewUpdateRequest request) {
        Review review = getReviewById(id);

        if (profanityFilterService.containsProfanity(request.reviewText())) {
            throw new IllegalArgumentException("Review contains inappropriate language. Please keep it professional.");
        }

        review.setRating(request.rating());
        review.setFinalGrade(request.finalGrade());
        review.setReviewText(request.reviewText());
        applyTerm(review, request.termType(), request.termYear());
        review.setProfessor(request.professor());
        review.setWorkload(request.workload());
        review.setHasExam(request.hasExam());
        review.setWouldTakeAgain(request.wouldTakeAgain());
        // Wrap in a mutable set: an immutable Set.of() (or Jackson's immutable empty
        // set) blows up Hibernate's merge with UnsupportedOperationException when it
        // clears the collection on update.
        review.setTags(new HashSet<>(request.tags() != null ? request.tags() : Set.of()));

        Review saved = reviewRepo.save(review);
        unitAggregateService.recalculateForUnit(saved.getUnit().getId());
        log.info("Review {} updated", id);
        return saved;
    }

    public ReviewCreationResult createReviewWithCampaignEntry(ReviewCreateRequest request) {
        Review savedReview = createReview(request);
        CampaignService.CampaignAwardResult award = campaignService.tryAwardCampaignEntries(savedReview.getUser(), savedReview);
        return new ReviewCreationResult(savedReview, award.newEntries());
    }

    // A single review can now mint entries in several campaigns at once, so this
    // carries the full list of entries created rather than a single one.
    public record ReviewCreationResult(
            Review review,
            List<CampaignEntry> newEntries
    ) {}

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

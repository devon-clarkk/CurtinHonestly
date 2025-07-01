package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.repo.ReviewRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@Transactional(rollbackOn = Exception.class)
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepo reviewRepo;
    private final UnitService unitService;

    // This method maps unit CODES to internal UUID's. Allowing for clean customer facing code-based endpoints, while maintaining UUID internally for clarity and safety.
    public List<Review> getReviewsByUnitCode(String unitCode) {
        Unit unit = unitService.getUnitByCode(unitCode);
        return reviewRepo.findByUnit_Id(unit.getId());
    }
    public Page<Review> getAllReviews(int page, int size)
    {
        return reviewRepo.findAll(PageRequest.of(page, size, Sort.by("date")));
    }

    public Review getReview(String id) throws RuntimeException
    {
        return reviewRepo.findById(id).orElseThrow(() -> new RuntimeException("Review not found"));
    }

    public Review createReview(Review review)
    {
        log.info("Review added");
        return reviewRepo.save(review);
    }
    public void deleteReview(Review review)
    {
        log.info("Review deleted");
        reviewRepo.delete(review);
    }
}

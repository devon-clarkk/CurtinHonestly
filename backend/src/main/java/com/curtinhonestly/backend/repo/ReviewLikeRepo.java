package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewLikeRepo extends JpaRepository<ReviewLike, String> {
    boolean existsByUser_IdAndReview_Id(String userId, String reviewId);

    Optional<ReviewLike> findByUser_IdAndReview_Id(String userId, String reviewId);

    long countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(
            String userId, Instant start, Instant end);

    long countByReview_Id(String reviewId);

    List<ReviewLike> findByUser_IdAndReview_IdIn(String userId, Collection<String> reviewIds);
}

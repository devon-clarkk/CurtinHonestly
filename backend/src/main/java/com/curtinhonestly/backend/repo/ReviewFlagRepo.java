package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.ReviewFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewFlagRepo extends JpaRepository<ReviewFlag, String> {
    boolean existsByUser_IdAndReview_Id(String userId, String reviewId);

    long countByReview_Id(String reviewId);

    void deleteByReview_Id(String reviewId);

    // Distinct flagged review IDs, most-flagged first, for the admin queue.
    @Query("""
            select rf.review.id from ReviewFlag rf
            group by rf.review.id
            order by count(rf.id) desc
            """)
    List<String> findDistinctFlaggedReviewIdsOrderByFlagCountDesc();
}

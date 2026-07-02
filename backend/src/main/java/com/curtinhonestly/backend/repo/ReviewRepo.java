package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepo extends JpaRepository<Review, String> {
    Optional<Review> findById(String id);
    List<Review> findByUnit_Id(String unitId);
    long countByCreatedAtAfter(Instant since);
    List<Review> findByCreatedAtAfter(Instant since);
}

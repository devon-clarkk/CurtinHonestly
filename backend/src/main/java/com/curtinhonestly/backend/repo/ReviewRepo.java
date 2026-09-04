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
    List<Review> findByUnit_IdOrderByCreatedAtDesc(String unitId);
    List<Review> findByUser_IdOrderByCreatedAtDesc(String userId);
    boolean existsByUser_IdAndUnit_Id(String userId, String unitId);
    long countByCreatedAtAfter(Instant since);
    List<Review> findByCreatedAtAfter(Instant since);

    // Reviews written by users who signed up through a referral link (tracking-only
    // attribution) or who are enrolled in a reward campaign, respectively.
    long countByUser_RegisteredViaRefIgnoreCase(String ref);
    long countByUser_Campaigns_Id(String campaignId);

    // Admin dashboard aggregates (AdminService). Count and group-by queries only,
    // so the dashboard never loads the reviews table into memory. Object[] rows
    // are documented per method.
    long countByUserIsNotNull();
    long countByWouldTakeAgainTrue();
    long countByFinalGradeIsNotNull();
    long countByCreatedAtBetween(Instant start, Instant end);
    List<Review> findTop10ByOrderByLikeCountDescCreatedAtDesc();

    // Only the timestamps, for bucketing into a daily series.
    @org.springframework.data.jpa.repository.Query("select r.createdAt from Review r where r.createdAt >= :since")
    List<Instant> findCreatedAtSince(@org.springframework.data.repository.query.Param("since") Instant since);

    @org.springframework.data.jpa.repository.Query(
            "select count(r) from Review r where r.reviewText is not null and length(trim(r.reviewText)) > 0")
    long countWithText();

    @org.springframework.data.jpa.repository.Query("select count(distinct r.unit.id) from Review r")
    long countDistinctReviewedUnits();

    @org.springframework.data.jpa.repository.Query("select count(distinct r.user.id) from Review r where r.user is not null")
    long countDistinctAuthors();

    @org.springframework.data.jpa.repository.Query(
            "select avg(length(r.reviewText)) from Review r where r.reviewText is not null")
    Double averageReviewTextLength();

    @org.springframework.data.jpa.repository.Query("select avg(r.workload) from Review r")
    Double averageWorkload();

    // Rows are [rating, count].
    @org.springframework.data.jpa.repository.Query("select r.rating, count(r) from Review r group by r.rating")
    List<Object[]> countGroupedByRating();

    // Rows are [workload, count].
    @org.springframework.data.jpa.repository.Query("select r.workload, count(r) from Review r group by r.workload")
    List<Object[]> countGroupedByWorkload();

    // Rows are [AcademicTerm, termYear, count]; termYear is null for EARLIER_UNSPECIFIED.
    @org.springframework.data.jpa.repository.Query(
            "select r.termType, r.termYear, count(r) from Review r where r.termType is not null group by r.termType, r.termYear")
    List<Object[]> countGroupedByTerm();

    // Rows are [Faculty, reviewCount, distinctUnitCount].
    @org.springframework.data.jpa.repository.Query(
            "select u.faculty, count(r), count(distinct u.id) from Review r join r.unit u group by u.faculty")
    List<Object[]> countReviewsAndUnitsGroupedByFaculty();

    // Rows are [unitCode, unitName, reviewCount, averageRating], most reviewed first.
    @org.springframework.data.jpa.repository.Query(
            "select u.code, u.name, count(r), avg(r.rating) from Review r join r.unit u " +
            "group by u.id, u.code, u.name order by count(r) desc, u.code asc")
    List<Object[]> findTopUnitsByReviewCount(org.springframework.data.domain.Pageable pageable);

    // Rows are [authorEmail, reviewCount, likesReceived], most prolific first.
    @org.springframework.data.jpa.repository.Query(
            "select a.email, count(r), coalesce(sum(r.likeCount), 0) from Review r join r.user a " +
            "group by a.id, a.email order by count(r) desc, coalesce(sum(r.likeCount), 0) desc, a.email asc")
    List<Object[]> findTopReviewers(org.springframework.data.domain.Pageable pageable);

    // Reviewer rank (ReviewerRankService): one aggregate row per author for a whole
    // set of user ids, so a unit page ranks every reviewer in a single query.
    // Grouped and filtered, so sum() is never null here; a user with no reviews
    // simply has no row and the caller treats that as zero.
    @org.springframework.data.jpa.repository.Query("""
            select r.user.id as userId,
                   count(r) as reviewCount,
                   sum(r.likeCount) as likesReceived
            from Review r
            where r.user.id in :userIds
            group by r.user.id
            """)
    List<ReviewerStats> aggregateReviewerStats(
            @org.springframework.data.repository.query.Param("userIds") java.util.Collection<String> userIds);

    interface ReviewerStats {
        String getUserId();
        Long getReviewCount();
        Long getLikesReceived();
    }

    // Recommendation model (RecommendationService): the whole review set in one
    // query with unit, author and tags already fetched, so building the in-memory
    // model does not issue a select per row. Rebuilt at most every ten minutes.
    @org.springframework.data.jpa.repository.Query(
            "select distinct r from Review r join fetch r.unit left join fetch r.user left join fetch r.tags")
    List<Review> findAllWithUnitAndUser();
}

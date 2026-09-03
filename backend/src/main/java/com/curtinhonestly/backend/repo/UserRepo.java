package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepo extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    long countByCreatedAtAfter(Instant since);
    List<User> findByCreatedAtAfter(Instant since);
    // Users enrolled in a campaign (via the user_campaigns many-to-many).
    long countByCampaigns_Id(String campaignId);

    // Signups attributed to a referral link. registeredViaRef stores the campaign
    // slug the user arrived through, so tracking-only links (which don't enrol the
    // user in the campaign) can still count their signups.
    long countByRegisteredViaRefIgnoreCase(String ref);

    // Admin dashboard aggregates (AdminService).
    long countByVerifiedStudentTrue();
    long countByBannedTrue();
    long countByCreatedAtBetween(Instant start, Instant end);
    long countByVerifiedStudentFalseAndCreatedAtAfter(Instant since);

    // Only the timestamps, for bucketing into a daily series.
    @org.springframework.data.jpa.repository.Query("select u.createdAt from User u where u.createdAt >= :since")
    List<Instant> findCreatedAtSince(@org.springframework.data.repository.query.Param("since") Instant since);
}

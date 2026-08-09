package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.Campaign;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampaignRepo extends JpaRepository<Campaign, String> {
    Optional<Campaign> findBySlugIgnoreCase(String slug);
    Optional<Campaign> findByCodeIgnoreCase(String code);
    List<Campaign> findAllByOrderByCreatedAtDesc();

    // Best-effort visit counter for referral links. A single atomic UPDATE (rather
    // than read-modify-write) so concurrent clicks don't lose increments. Returns
    // the number of rows touched (0 if no campaign has that slug).
    @Modifying
    @Query("UPDATE Campaign c SET c.visitCount = c.visitCount + 1 WHERE lower(c.slug) = lower(:slug)")
    int incrementVisitCountBySlug(@Param("slug") String slug);

    // Locks the campaign row for the duration of the caller's transaction, so a
    // maxRedemptions count-and-insert can't race with a concurrent registration
    // on the last remaining slot.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Campaign c WHERE c.id = :id")
    Optional<Campaign> findByIdForUpdate(@Param("id") String id);
}

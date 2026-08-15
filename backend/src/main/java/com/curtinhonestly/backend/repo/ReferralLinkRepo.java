package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.ReferralLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReferralLinkRepo extends JpaRepository<ReferralLink, String> {
    Optional<ReferralLink> findBySlugIgnoreCase(String slug);
    List<ReferralLink> findAllByOrderByCreatedAtDesc();

    // Atomic visit increment, mirroring CampaignRepo.incrementVisitCountBySlug.
    @Modifying
    @Query("UPDATE ReferralLink l SET l.visitCount = l.visitCount + 1 WHERE lower(l.slug) = lower(:slug)")
    int incrementVisitCountBySlug(@Param("slug") String slug);
}

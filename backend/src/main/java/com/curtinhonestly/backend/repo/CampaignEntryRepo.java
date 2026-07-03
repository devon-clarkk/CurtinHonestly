package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.CampaignEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampaignEntryRepo extends JpaRepository<CampaignEntry, String> {
    long countByCampaign_IdAndUser_Id(String campaignId, String userId);
    long countByCampaign_Id(String campaignId);

    @Query("""
            SELECT e FROM CampaignEntry e
            JOIN FETCH e.review r
            JOIN FETCH r.unit
            JOIN FETCH e.campaign
            WHERE e.user.id = :userId
            ORDER BY e.createdAt DESC
            """)
    List<CampaignEntry> findByUser_IdOrderByCreatedAtDesc(@Param("userId") String userId);

    @Query("""
            SELECT e FROM CampaignEntry e
            JOIN FETCH e.review r
            JOIN FETCH r.unit
            JOIN FETCH e.user
            WHERE e.campaign.id = :campaignId
            ORDER BY e.createdAt DESC
            """)
    List<CampaignEntry> findByCampaign_IdOrderByCreatedAtDesc(@Param("campaignId") String campaignId);

    Optional<CampaignEntry> findByEntryTokenIgnoreCase(String entryToken);
    boolean existsByReview_Id(String reviewId);
}

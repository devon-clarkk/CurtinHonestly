package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignRepo extends JpaRepository<Campaign, String> {
    Optional<Campaign> findBySlugIgnoreCase(String slug);
    Optional<Campaign> findByCodeIgnoreCase(String code);
    List<Campaign> findAllByOrderByCreatedAtDesc();
}

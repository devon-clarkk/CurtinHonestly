package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.Club;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClubRepo extends JpaRepository<Club, String> {
    Optional<Club> findBySlug(String slug);
    Optional<Club> findBySlugAndActiveTrue(String slug);
    List<Club> findByActiveTrueOrderByNameAsc();
    List<Club> findAllByOrderByNameAsc();
    boolean existsBySlug(String slug);
    boolean existsByNameIgnoreCase(String name);
}

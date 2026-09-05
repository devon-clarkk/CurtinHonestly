package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.ClubMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClubMemberRepo extends JpaRepository<ClubMember, String> {

    /** Every club a user belongs to, club fetched, for the portal switcher. */
    @EntityGraph(attributePaths = {"club"})
    List<ClubMember> findByUser_IdOrderByCreatedAtAsc(String userId);

    /** Every member of a club, user fetched, for the admin members table. */
    @EntityGraph(attributePaths = {"user"})
    List<ClubMember> findByClub_IdOrderByCreatedAtAsc(String clubId);

    Optional<ClubMember> findByClub_IdAndUser_Id(String clubId, String userId);

    long countByUser_Id(String userId);

    long countByClub_Id(String clubId);
}

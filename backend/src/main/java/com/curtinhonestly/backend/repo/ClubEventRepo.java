package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.ClubEvent;
import com.curtinhonestly.backend.domain.ClubEventStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClubEventRepo extends JpaRepository<ClubEvent, String> {

    /** Published rows with club and target unit fetched in the same query, ready to snapshot. */
    @EntityGraph(attributePaths = {"club", "targetUnit"})
    List<ClubEvent> findByStatusOrderByStartsAtAsc(ClubEventStatus status);

    /** One club's events for its portal, newest start first. */
    @EntityGraph(attributePaths = {"club", "targetUnit"})
    List<ClubEvent> findByClub_IdOrderByStartsAtDesc(String clubId);

    @EntityGraph(attributePaths = {"club", "targetUnit", "createdBy"})
    List<ClubEvent> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"club", "targetUnit", "createdBy"})
    List<ClubEvent> findByStatusOrderByCreatedAtDesc(ClubEventStatus status);

    @EntityGraph(attributePaths = {"club", "targetUnit", "createdBy"})
    Optional<ClubEvent> findWithDetailsById(String id);

    long countByClub_Id(String clubId);

    long countByClub_IdAndStatus(String clubId, ClubEventStatus status);

    /** Atomic view increment; only published rows count. Returns rows updated (0 or 1). */
    @Modifying
    @Query("update ClubEvent e set e.viewCount = e.viewCount + 1 "
            + "where e.id = :id and e.status = com.curtinhonestly.backend.domain.ClubEventStatus.PUBLISHED")
    int incrementViews(@Param("id") String id);
}

package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.ResourceStatus;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.UnitResourceLink;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UnitResourceLinkRepo extends JpaRepository<UnitResourceLink, String> {

    /** Approved rows with their target unit fetched in the same query, ready to snapshot. */
    @EntityGraph(attributePaths = {"targetUnit"})
    List<UnitResourceLink> findByStatusOrderBySortOrderAscTitleAsc(ResourceStatus status);

    @EntityGraph(attributePaths = {"targetUnit", "submittedBy"})
    List<UnitResourceLink> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"targetUnit", "submittedBy"})
    List<UnitResourceLink> findByStatusOrderByCreatedAtDesc(ResourceStatus status);

    /** Atomic click increment; only approved rows count. Returns rows updated (0 or 1). */
    @Modifying
    @Query("update UnitResourceLink r set r.clickCount = r.clickCount + 1 "
            + "where r.id = :id and r.status = com.curtinhonestly.backend.domain.ResourceStatus.APPROVED")
    int incrementClicks(@Param("id") String id);

    /** The three unit fields a rule is matched against, for the admin preview. */
    interface UnitKey {
        String getCode();
        Faculty getFaculty();
        UnitLevel getLevel();
    }

    /**
     * Declared here rather than on UnitRepo so this feature stays self-contained;
     * JPQL does not care which repository interface a query is declared on.
     */
    @Query("select u.code as code, u.faculty as faculty, u.level as level from Unit u order by u.code")
    List<UnitKey> unitKeysForPreview();
}

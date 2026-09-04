package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.BoardScope;
import com.curtinhonestly.backend.domain.BoardThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BoardThreadRepo extends JpaRepository<BoardThread, String> {

    Optional<BoardThread> findByIdAndDeletedAtIsNull(String id);

    // Public lists: soft-deleted threads never appear. Ordering (pinned first,
    // then activity or age) comes in on the Pageable so one query serves both sorts.
    Page<BoardThread> findByScopeAndDeletedAtIsNull(BoardScope scope, Pageable pageable);

    Page<BoardThread> findByUnit_IdAndDeletedAtIsNull(String unitId, Pageable pageable);

    long countByUnit_IdAndDeletedAtIsNull(String unitId);

    // Admin listing: everything that is not removed, newest first via the Pageable.
    Page<BoardThread> findByDeletedAtIsNull(Pageable pageable);
}

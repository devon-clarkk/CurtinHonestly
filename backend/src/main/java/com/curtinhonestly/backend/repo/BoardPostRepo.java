package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.BoardPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardPostRepo extends JpaRepository<BoardPost, String> {

    // Deleted posts are included on purpose: the thread view renders them as
    // "[removed]" placeholders so the reply order still makes sense.
    Page<BoardPost> findByThread_Id(String threadId, Pageable pageable);

    long countByThread_Unit_IdAndDeletedAtIsNullAndThread_DeletedAtIsNull(String unitId);

    Page<BoardPost> findByDeletedAtIsNull(Pageable pageable);
}

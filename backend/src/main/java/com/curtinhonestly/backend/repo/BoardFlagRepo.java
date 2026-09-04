package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.BoardFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BoardFlagRepo extends JpaRepository<BoardFlag, String> {

    boolean existsByReporter_IdAndThread_Id(String reporterId, String threadId);

    boolean existsByReporter_IdAndPost_Id(String reporterId, String postId);

    long countByThread_Id(String threadId);

    long countByPost_Id(String postId);

    void deleteByThread_Id(String threadId);

    void deleteByPost_Id(String postId);

    // Flagged targets grouped for the admin queue, most-flagged first.
    // Rows are [targetId, flagCount, latestFlagAt].
    @Query("""
            select f.thread.id, count(f), max(f.createdAt) from BoardFlag f
            where f.thread is not null
            group by f.thread.id
            order by count(f) desc
            """)
    List<Object[]> countGroupedByThread();

    @Query("""
            select f.post.id, count(f), max(f.createdAt) from BoardFlag f
            where f.post is not null
            group by f.post.id
            order by count(f) desc
            """)
    List<Object[]> countGroupedByPost();

    // Flag counts for one page of admin rows in a single query. Rows are [targetId, count].
    @Query("""
            select f.thread.id, count(f) from BoardFlag f
            where f.thread.id in :ids
            group by f.thread.id
            """)
    List<Object[]> countGroupedByThreadIds(@Param("ids") Collection<String> ids);

    @Query("""
            select f.post.id, count(f) from BoardFlag f
            where f.post.id in :ids
            group by f.post.id
            """)
    List<Object[]> countGroupedByPostIds(@Param("ids") Collection<String> ids);

    List<BoardFlag> findByThread_IdOrderByCreatedAtDesc(String threadId);

    List<BoardFlag> findByPost_IdOrderByCreatedAtDesc(String postId);
}

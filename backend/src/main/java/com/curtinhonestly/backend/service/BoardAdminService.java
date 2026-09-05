package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.BoardFlag;
import com.curtinhonestly.backend.domain.BoardPost;
import com.curtinhonestly.backend.domain.BoardThread;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.BoardAdminFlaggedItemDTO;
import com.curtinhonestly.backend.dto.BoardAdminPostDTO;
import com.curtinhonestly.backend.dto.BoardAdminThreadDTO;
import com.curtinhonestly.backend.repo.BoardFlagRepo;
import com.curtinhonestly.backend.repo.BoardPostRepo;
import com.curtinhonestly.backend.repo.BoardThreadRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Moderation for the community boards: the flag queue, recent content, and
 * pin / lock / remove. Removal is the same soft delete students get, plus the
 * flags are cleared so the item leaves the queue.
 */
@ConditionalOnProperty(prefix = "app.boards", name = "enabled", havingValue = "true")
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(rollbackOn = Exception.class)
public class BoardAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BoardThreadRepo threadRepo;
    private final BoardPostRepo postRepo;
    private final BoardFlagRepo flagRepo;
    private final BoardService boardService;

    public Page<BoardAdminThreadDTO> listThreads(int page, int size) {
        Page<BoardThread> threads = threadRepo.findByDeletedAtIsNull(
                PageRequest.of(Math.max(0, page), clamp(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<String, Long> flags = flagCounts(
                flagRepo.countGroupedByThreadIds(threads.getContent().stream().map(BoardThread::getId).toList()));
        return threads.map(thread -> toThreadDTO(thread, flags.getOrDefault(thread.getId(), 0L)));
    }

    public Page<BoardAdminPostDTO> listPosts(int page, int size) {
        Page<BoardPost> posts = postRepo.findByDeletedAtIsNull(
                PageRequest.of(Math.max(0, page), clamp(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<String, Long> flags = flagCounts(
                flagRepo.countGroupedByPostIds(posts.getContent().stream().map(BoardPost::getId).toList()));
        return posts.map(post -> toPostDTO(post, flags.getOrDefault(post.getId(), 0L)));
    }

    /** Every flagged thread and post still live, most-flagged first. */
    public List<BoardAdminFlaggedItemDTO> flaggedContent() {
        List<BoardAdminFlaggedItemDTO> items = new ArrayList<>();
        for (Object[] row : flagRepo.countGroupedByThread()) {
            String threadId = (String) row[0];
            long count = ((Number) row[1]).longValue();
            Instant latest = (Instant) row[2];
            threadRepo.findById(threadId)
                    .filter(thread -> !thread.isDeleted())
                    .map(thread -> toFlaggedThread(thread, count, latest))
                    .ifPresent(items::add);
        }
        for (Object[] row : flagRepo.countGroupedByPost()) {
            String postId = (String) row[0];
            long count = ((Number) row[1]).longValue();
            Instant latest = (Instant) row[2];
            postRepo.findById(postId)
                    .filter(post -> !post.isDeleted())
                    .map(post -> toFlaggedPost(post, count, latest))
                    .ifPresent(items::add);
        }
        items.sort(Comparator.comparingLong(BoardAdminFlaggedItemDTO::flagCount).reversed()
                .thenComparing(BoardAdminFlaggedItemDTO::latestFlagAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    public BoardAdminThreadDTO setLocked(String threadId, boolean locked) {
        BoardThread thread = requireThread(threadId);
        thread.setLocked(locked);
        BoardThread saved = threadRepo.save(thread);
        log.info("Board thread {} {}", threadId, locked ? "locked" : "unlocked");
        return toThreadDTO(saved, flagRepo.countByThread_Id(threadId));
    }

    public BoardAdminThreadDTO setPinned(String threadId, boolean pinned) {
        BoardThread thread = requireThread(threadId);
        thread.setPinned(pinned);
        BoardThread saved = threadRepo.save(thread);
        log.info("Board thread {} {}", threadId, pinned ? "pinned" : "unpinned");
        return toThreadDTO(saved, flagRepo.countByThread_Id(threadId));
    }

    /** Soft delete plus clear flags, so the item leaves both the site and the queue. */
    public void removeThread(String threadId) {
        BoardThread thread = requireThread(threadId);
        if (!thread.isDeleted()) {
            thread.setDeletedAt(Instant.now());
            threadRepo.save(thread);
        }
        flagRepo.deleteByThread_Id(threadId);
        log.info("Board thread {} removed by admin", threadId);
    }

    public void removePost(String postId) {
        BoardPost post = postRepo.findById(postId)
                .orElseThrow(() -> new BoardNotFoundException("Post not found."));
        if (!post.isDeleted()) {
            post.setDeletedAt(Instant.now());
            postRepo.save(post);
            BoardThread thread = post.getThread();
            thread.setReplyCount(Math.max(0, thread.getReplyCount() - 1));
            threadRepo.save(thread);
        }
        flagRepo.deleteByPost_Id(postId);
        log.info("Board post {} removed by admin", postId);
    }

    public void dismissThreadFlags(String threadId) {
        flagRepo.deleteByThread_Id(threadId);
        log.info("Flags dismissed for board thread {}", threadId);
    }

    public void dismissPostFlags(String postId) {
        flagRepo.deleteByPost_Id(postId);
        log.info("Flags dismissed for board post {}", postId);
    }

    // Mapping

    private BoardAdminThreadDTO toThreadDTO(BoardThread thread, long flagCount) {
        Unit unit = thread.getUnit();
        User author = thread.getAuthor();
        return new BoardAdminThreadDTO(
                thread.getId(),
                thread.getScope(),
                unit == null ? null : unit.getCode(),
                unit == null ? null : unit.getName(),
                thread.getTitle(),
                thread.getBody(),
                boardService.pseudonymFor(author),
                author == null ? null : author.getEmail(),
                author != null && author.isVerifiedStudent(),
                thread.getReplyCount(),
                thread.isPinned(),
                thread.isLocked(),
                flagCount,
                thread.getCreatedAt(),
                thread.getEditedAt(),
                thread.getLastActivityAt(),
                thread.getDeletedAt());
    }

    private BoardAdminPostDTO toPostDTO(BoardPost post, long flagCount) {
        BoardThread thread = post.getThread();
        User author = post.getAuthor();
        return new BoardAdminPostDTO(
                post.getId(),
                thread.getId(),
                thread.getTitle(),
                thread.getUnit() == null ? null : thread.getUnit().getCode(),
                post.getBody(),
                boardService.pseudonymFor(author),
                author == null ? null : author.getEmail(),
                author != null && author.isVerifiedStudent(),
                flagCount,
                post.getCreatedAt(),
                post.getEditedAt(),
                post.getDeletedAt());
    }

    private BoardAdminFlaggedItemDTO toFlaggedThread(BoardThread thread, long count, Instant latest) {
        User author = thread.getAuthor();
        return new BoardAdminFlaggedItemDTO(
                "THREAD",
                thread.getId(),
                thread.getId(),
                thread.getTitle(),
                thread.getUnit() == null ? null : thread.getUnit().getCode(),
                thread.getBody(),
                boardService.pseudonymFor(author),
                author == null ? null : author.getEmail(),
                count,
                reasons(flagRepo.findByThread_IdOrderByCreatedAtDesc(thread.getId())),
                latest,
                thread.getCreatedAt());
    }

    private BoardAdminFlaggedItemDTO toFlaggedPost(BoardPost post, long count, Instant latest) {
        BoardThread thread = post.getThread();
        User author = post.getAuthor();
        return new BoardAdminFlaggedItemDTO(
                "POST",
                post.getId(),
                thread.getId(),
                thread.getTitle(),
                thread.getUnit() == null ? null : thread.getUnit().getCode(),
                post.getBody(),
                boardService.pseudonymFor(author),
                author == null ? null : author.getEmail(),
                count,
                reasons(flagRepo.findByPost_IdOrderByCreatedAtDesc(post.getId())),
                latest,
                post.getCreatedAt());
    }

    private static List<String> reasons(Collection<BoardFlag> flags) {
        return flags.stream().map(BoardFlag::getReason).filter(Objects::nonNull).toList();
    }

    private static Map<String, Long> flagCounts(List<Object[]> rows) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private BoardThread requireThread(String threadId) {
        return threadRepo.findById(threadId)
                .orElseThrow(() -> new BoardNotFoundException("Thread not found."));
    }

    private static int clamp(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }
}

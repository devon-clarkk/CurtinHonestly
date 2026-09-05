package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.BoardFlag;
import com.curtinhonestly.backend.domain.BoardPost;
import com.curtinhonestly.backend.domain.BoardScope;
import com.curtinhonestly.backend.domain.BoardThread;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.dto.BoardAuthorDTO;
import com.curtinhonestly.backend.dto.BoardPostDTO;
import com.curtinhonestly.backend.dto.BoardThreadDetailDTO;
import com.curtinhonestly.backend.dto.BoardThreadSummaryDTO;
import com.curtinhonestly.backend.dto.BoardUnitSummaryDTO;
import com.curtinhonestly.backend.repo.BoardFlagRepo;
import com.curtinhonestly.backend.repo.BoardPostRepo;
import com.curtinhonestly.backend.repo.BoardThreadRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.util.Pseudonym;
import com.curtinhonestly.backend.util.ReviewerRank;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Community boards: the general board plus one board per unit. Threads and
 * replies are anonymous but consistent (see {@link Pseudonym}), soft deleted,
 * and profanity filtered on the way in. Moderation lives in
 * {@link BoardAdminService}; this class is what the public API calls.
 */
@ConditionalOnProperty(prefix = "app.boards", name = "enabled", havingValue = "true")
@Service
@Slf4j
@Transactional(rollbackOn = Exception.class)
public class BoardService {

    public static final int MAX_TITLE_LENGTH = 140;
    public static final int MAX_BODY_LENGTH = 4000;
    public static final Duration EDIT_WINDOW = Duration.ofMinutes(15);
    public static final String REMOVED_PLACEHOLDER = "[removed]";
    public static final int MAX_PAGE_SIZE = 100;

    private static final int EXCERPT_LENGTH = 160;
    private static final int PREVIEW_THREADS = 3;
    private static final int MAX_RECENT = 50;

    private final BoardThreadRepo threadRepo;
    private final BoardPostRepo postRepo;
    private final BoardFlagRepo flagRepo;
    private final UnitRepo unitRepo;
    private final UserRepo userRepo;
    private final ProfanityFilterService profanityFilterService;
    private final ReviewerRankService reviewerRankService;
    private final String pseudonymSecret;

    public BoardService(BoardThreadRepo threadRepo,
                        BoardPostRepo postRepo,
                        BoardFlagRepo flagRepo,
                        UnitRepo unitRepo,
                        UserRepo userRepo,
                        ProfanityFilterService profanityFilterService,
                        ReviewerRankService reviewerRankService,
                        @Value("${app.boards.pseudonym-secret}") String pseudonymSecret) {
        this.threadRepo = threadRepo;
        this.postRepo = postRepo;
        this.flagRepo = flagRepo;
        this.unitRepo = unitRepo;
        this.userRepo = userRepo;
        this.profanityFilterService = profanityFilterService;
        this.reviewerRankService = reviewerRankService;
        this.pseudonymSecret = pseudonymSecret;
    }

    // Reads

    public Page<BoardThreadSummaryDTO> listGeneralThreads(int page, int size, String sort) {
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size), threadSort(sort));
        return summarise(threadRepo.findByScopeAndDeletedAtIsNull(BoardScope.GENERAL, pageable));
    }

    public Page<BoardThreadSummaryDTO> listUnitThreads(String unitCode, int page, int size, String sort) {
        Unit unit = requireUnit(unitCode);
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size), threadSort(sort));
        return summarise(threadRepo.findByUnit_IdAndDeletedAtIsNull(unit.getId(), pageable));
    }

    public BoardUnitSummaryDTO unitSummary(String unitCode) {
        Unit unit = requireUnit(unitCode);
        long threadCount = threadRepo.countByUnit_IdAndDeletedAtIsNull(unit.getId());
        long postCount = postRepo.countByThread_Unit_IdAndDeletedAtIsNullAndThread_DeletedAtIsNull(unit.getId());
        Pageable latest = PageRequest.of(0, PREVIEW_THREADS, threadSort("activity"));
        List<BoardThreadSummaryDTO> threads = summarise(
                threadRepo.findByUnit_IdAndDeletedAtIsNull(unit.getId(), latest)).getContent();
        return new BoardUnitSummaryDTO(unit.getCode(), unit.getName(), threadCount, postCount, threads);
    }

    /** Recently active threads across every unit board, newest activity first. */
    public List<BoardThreadSummaryDTO> recentUnitThreads(int limit) {
        int n = Math.max(1, Math.min(limit, MAX_RECENT));
        Pageable pageable = PageRequest.of(0, n, Sort.by(Sort.Order.desc("lastActivityAt")));
        return summarise(threadRepo.findByScopeAndDeletedAtIsNull(BoardScope.UNIT, pageable)).getContent();
    }

    public BoardThreadDetailDTO getThread(String threadId, int page, int size) {
        BoardThread thread = requireLiveThread(threadId);
        return toDetail(thread, postsPage(threadId, page, size));
    }

    // Writes

    public BoardThreadDetailDTO createGeneralThread(String title, String body) {
        return createThread(null, title, body);
    }

    public BoardThreadDetailDTO createUnitThread(String unitCode, String title, String body) {
        return createThread(requireUnit(unitCode), title, body);
    }

    private BoardThreadDetailDTO createThread(Unit unit, String rawTitle, String rawBody) {
        String title = cleanTitle(rawTitle);
        String body = cleanBody(rawBody);
        User user = currentUser();

        BoardThread thread = new BoardThread();
        thread.setUnit(unit);
        thread.setScope(unit == null ? BoardScope.GENERAL : BoardScope.UNIT);
        thread.setTitle(title);
        thread.setBody(body);
        thread.setAuthor(user);
        Instant now = Instant.now();
        thread.setCreatedAt(now);
        thread.setLastActivityAt(now);

        BoardThread saved = threadRepo.save(thread);
        log.info("User {} started board thread {} ({})", user.getId(), saved.getId(),
                unit == null ? "general" : unit.getCode());
        return toDetail(saved, Page.empty());
    }

    public BoardPostDTO createPost(String threadId, String rawBody) {
        BoardThread thread = requireLiveThread(threadId);
        if (thread.isLocked()) {
            throw new BoardForbiddenException("This thread is locked. New replies are closed.");
        }
        String body = cleanBody(rawBody);
        User user = currentUser();

        BoardPost post = new BoardPost();
        post.setThread(thread);
        post.setAuthor(user);
        post.setBody(body);
        post.setCreatedAt(Instant.now());
        BoardPost saved = postRepo.save(post);

        thread.setReplyCount(thread.getReplyCount() + 1);
        thread.setLastActivityAt(saved.getCreatedAt());
        threadRepo.save(thread);

        log.info("User {} replied to board thread {}", user.getId(), threadId);
        Map<String, ReviewerRank> ranks = reviewerRankService.ranksFor(Set.of(user.getId()));
        return toPost(saved, ranks, user.getId(), currentUserIsAdmin(), authorId(thread.getAuthor()));
    }

    /** Owner-or-admin is enforced by @PreAuthorize on the resource; the time window is enforced here. */
    public BoardThreadDetailDTO updateThread(String threadId, String rawTitle, String rawBody) {
        BoardThread thread = requireLiveThread(threadId);
        enforceEditWindow(thread.getCreatedAt());
        if (rawTitle != null && !rawTitle.isBlank()) {
            thread.setTitle(cleanTitle(rawTitle));
        }
        thread.setBody(cleanBody(rawBody));
        thread.setEditedAt(Instant.now());
        BoardThread saved = threadRepo.save(thread);
        return toDetail(saved, postsPage(threadId, 0, MAX_PAGE_SIZE));
    }

    public BoardPostDTO updatePost(String postId, String rawBody) {
        BoardPost post = requireLivePost(postId);
        enforceEditWindow(post.getCreatedAt());
        post.setBody(cleanBody(rawBody));
        post.setEditedAt(Instant.now());
        BoardPost saved = postRepo.save(post);
        String currentUserId = currentUserIdIfAuthenticated().orElse(null);
        Map<String, ReviewerRank> ranks = reviewerRankService.ranksFor(authorIds(saved.getAuthor()));
        return toPost(saved, ranks, currentUserId, currentUserIsAdmin(), authorId(saved.getThread().getAuthor()));
    }

    public void deleteThread(String threadId) {
        BoardThread thread = requireLiveThread(threadId);
        thread.setDeletedAt(Instant.now());
        threadRepo.save(thread);
        log.info("Board thread {} removed", threadId);
    }

    public void deletePost(String postId) {
        BoardPost post = requireLivePost(postId);
        post.setDeletedAt(Instant.now());
        postRepo.save(post);

        BoardThread thread = post.getThread();
        thread.setReplyCount(Math.max(0, thread.getReplyCount() - 1));
        threadRepo.save(thread);
        log.info("Board post {} removed", postId);
    }

    /** Idempotent, like ReviewFlagService: a repeat report is a no-op. */
    public void flagThread(String threadId, String reason) {
        BoardThread thread = requireLiveThread(threadId);
        User reporter = currentUser();
        if (flagRepo.existsByReporter_IdAndThread_Id(reporter.getId(), threadId)) {
            return;
        }
        BoardFlag flag = new BoardFlag();
        flag.setReporter(reporter);
        flag.setThread(thread);
        flag.setReason(cleanReason(reason));
        flagRepo.save(flag);
        log.info("User {} flagged board thread {}", reporter.getId(), threadId);
    }

    public void flagPost(String postId, String reason) {
        BoardPost post = requireLivePost(postId);
        User reporter = currentUser();
        if (flagRepo.existsByReporter_IdAndPost_Id(reporter.getId(), postId)) {
            return;
        }
        BoardFlag flag = new BoardFlag();
        flag.setReporter(reporter);
        flag.setPost(post);
        flag.setReason(cleanReason(reason));
        flagRepo.save(flag);
        log.info("User {} flagged board post {}", reporter.getId(), postId);
    }

    // Mapping (shared with BoardAdminService)

    public BoardAuthorDTO toAuthor(User author, Map<String, ReviewerRank> ranks) {
        if (author == null) {
            return new BoardAuthorDTO(Pseudonym.FORMER_STUDENT, false, null, null, null, null);
        }
        String pseudonym = pseudonymFor(author);
        ReviewerRank rank = ranks == null ? null : ranks.get(author.getId());
        if (rank == null || !rank.hasActivity()) {
            return new BoardAuthorDTO(pseudonym, author.isVerifiedStudent(), null, null, null, null);
        }
        return new BoardAuthorDTO(
                pseudonym,
                author.isVerifiedStudent(),
                rank.activityTier(),
                rank.activityTier().getLabel(),
                rank.recognitionTier(),
                rank.recognitionTier() == null ? null : rank.recognitionTier().getLabel());
    }

    public String pseudonymFor(User author) {
        return author == null ? Pseudonym.FORMER_STUDENT : Pseudonym.forUser(author.getId(), pseudonymSecret);
    }

    public static String excerpt(String body) {
        if (body == null) {
            return "";
        }
        String collapsed = body.replaceAll("\\s+", " ").trim();
        if (collapsed.length() <= EXCERPT_LENGTH) {
            return collapsed;
        }
        return collapsed.substring(0, EXCERPT_LENGTH - 1).trim() + "...";
    }

    private Page<BoardThreadSummaryDTO> summarise(Page<BoardThread> threads) {
        Set<String> ids = new LinkedHashSet<>();
        for (BoardThread thread : threads.getContent()) {
            String id = authorId(thread.getAuthor());
            if (id != null) {
                ids.add(id);
            }
        }
        Map<String, ReviewerRank> ranks = reviewerRankService.ranksFor(ids);
        return threads.map(thread -> toSummary(thread, ranks));
    }

    private BoardThreadSummaryDTO toSummary(BoardThread thread, Map<String, ReviewerRank> ranks) {
        Unit unit = thread.getUnit();
        return new BoardThreadSummaryDTO(
                thread.getId(),
                thread.getScope(),
                unit == null ? null : unit.getCode(),
                unit == null ? null : unit.getName(),
                thread.getTitle(),
                excerpt(thread.getBody()),
                toAuthor(thread.getAuthor(), ranks),
                thread.getReplyCount(),
                thread.isPinned(),
                thread.isLocked(),
                thread.getCreatedAt(),
                thread.getLastActivityAt());
    }

    private BoardThreadDetailDTO toDetail(BoardThread thread, Page<BoardPost> posts) {
        Set<String> ids = new LinkedHashSet<>();
        String opId = authorId(thread.getAuthor());
        if (opId != null) {
            ids.add(opId);
        }
        for (BoardPost post : posts.getContent()) {
            String id = authorId(post.getAuthor());
            if (id != null && !post.isDeleted()) {
                ids.add(id);
            }
        }
        Map<String, ReviewerRank> ranks = reviewerRankService.ranksFor(ids);
        String currentUserId = currentUserIdIfAuthenticated().orElse(null);
        boolean admin = currentUserIsAdmin();
        boolean owned = currentUserId != null && currentUserId.equals(opId);

        List<BoardPostDTO> postDtos = posts.getContent().stream()
                .map(post -> toPost(post, ranks, currentUserId, admin, opId))
                .toList();

        Unit unit = thread.getUnit();
        return new BoardThreadDetailDTO(
                thread.getId(),
                thread.getScope(),
                unit == null ? null : unit.getCode(),
                unit == null ? null : unit.getName(),
                thread.getTitle(),
                thread.getBody(),
                toAuthor(thread.getAuthor(), ranks),
                thread.getReplyCount(),
                thread.isPinned(),
                thread.isLocked(),
                owned,
                admin || (owned && withinEditWindow(thread.getCreatedAt())),
                thread.getCreatedAt(),
                thread.getEditedAt(),
                thread.getLastActivityAt(),
                postDtos,
                posts.getNumber(),
                posts.getTotalPages(),
                posts.getTotalElements());
    }

    private BoardPostDTO toPost(BoardPost post, Map<String, ReviewerRank> ranks,
                                String currentUserId, boolean admin, String opId) {
        if (post.isDeleted()) {
            return new BoardPostDTO(post.getId(), post.getThread().getId(), REMOVED_PLACEHOLDER,
                    null, false, true, false, false, post.getCreatedAt(), null);
        }
        String authorId = authorId(post.getAuthor());
        boolean owned = currentUserId != null && currentUserId.equals(authorId);
        return new BoardPostDTO(
                post.getId(),
                post.getThread().getId(),
                post.getBody(),
                toAuthor(post.getAuthor(), ranks),
                authorId != null && authorId.equals(opId),
                false,
                owned,
                admin || (owned && withinEditWindow(post.getCreatedAt())),
                post.getCreatedAt(),
                post.getEditedAt());
    }

    // Helpers

    private Page<BoardPost> postsPage(String threadId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size), Sort.by(Sort.Order.asc("createdAt")));
        return postRepo.findByThread_Id(threadId, pageable);
    }

    private static Sort threadSort(String sort) {
        Sort.Order pinnedFirst = Sort.Order.desc("pinned");
        if ("newest".equalsIgnoreCase(sort)) {
            return Sort.by(pinnedFirst, Sort.Order.desc("createdAt"));
        }
        return Sort.by(pinnedFirst, Sort.Order.desc("lastActivityAt"));
    }

    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private Unit requireUnit(String unitCode) {
        String code = unitCode == null ? "" : unitCode.trim().toUpperCase();
        return unitRepo.findByCode(code)
                .orElseThrow(() -> new BoardNotFoundException("Unit not found: " + unitCode));
    }

    private BoardThread requireLiveThread(String threadId) {
        return threadRepo.findByIdAndDeletedAtIsNull(threadId)
                .orElseThrow(() -> new BoardNotFoundException("Thread not found."));
    }

    private BoardPost requireLivePost(String postId) {
        return postRepo.findById(postId)
                .filter(post -> !post.isDeleted() && !post.getThread().isDeleted())
                .orElseThrow(() -> new BoardNotFoundException("Post not found."));
    }

    private void enforceEditWindow(Instant createdAt) {
        if (currentUserIsAdmin()) {
            return;
        }
        if (!withinEditWindow(createdAt)) {
            throw new BoardForbiddenException("Posts can be edited for " + EDIT_WINDOW.toMinutes()
                    + " minutes after they are published.");
        }
    }

    private static boolean withinEditWindow(Instant createdAt) {
        return createdAt != null && Instant.now().isBefore(createdAt.plus(EDIT_WINDOW));
    }

    private String cleanTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isBlank()) {
            throw new IllegalArgumentException("A title is required.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Titles must be " + MAX_TITLE_LENGTH + " characters or fewer.");
        }
        if (profanityFilterService.containsProfanity(title)) {
            throw new IllegalArgumentException("Your title contains language that violates our community standards.");
        }
        return title;
    }

    private String cleanBody(String rawBody) {
        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isBlank()) {
            throw new IllegalArgumentException("Some text is required.");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("Posts must be " + MAX_BODY_LENGTH + " characters or fewer.");
        }
        if (profanityFilterService.containsProfanity(body)) {
            throw new IllegalArgumentException("Your post contains language that violates our community standards.");
        }
        return body;
    }

    private static String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }

    // Null-tolerant: anonymised content has a null author and contributes no id.
    private static Set<String> authorIds(User... users) {
        Set<String> ids = new LinkedHashSet<>();
        for (User user : users) {
            String id = authorId(user);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    static String authorId(User author) {
        return author == null ? null : author.getId();
    }

    public Optional<String> currentUserIdIfAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        return userRepo.findByEmail(name).map(User::getId);
    }

    boolean currentUserIsAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> UserRole.ROLE_ADMIN.name().equals(granted.getAuthority()));
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication == null ? null : authentication.getName();
        if (email == null) {
            throw new IllegalStateException("Sign in to post on the boards.");
        }
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }
}

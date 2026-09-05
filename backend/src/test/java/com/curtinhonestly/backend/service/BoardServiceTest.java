package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.BoardPost;
import com.curtinhonestly.backend.domain.BoardScope;
import com.curtinhonestly.backend.domain.BoardThread;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.BoardPostDTO;
import com.curtinhonestly.backend.dto.BoardThreadDetailDTO;
import com.curtinhonestly.backend.repo.BoardFlagRepo;
import com.curtinhonestly.backend.repo.BoardPostRepo;
import com.curtinhonestly.backend.repo.BoardThreadRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.util.Pseudonym;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    private static final String SECRET = "board-test-secret";
    private static final String ALICE = "alice@student.curtin.edu.au";
    private static final String BOB = "bob@student.curtin.edu.au";

    @Mock BoardThreadRepo threadRepo;
    @Mock BoardPostRepo postRepo;
    @Mock BoardFlagRepo flagRepo;
    @Mock UnitRepo unitRepo;
    @Mock UserRepo userRepo;
    @Mock ProfanityFilterService profanityFilterService;
    @Mock ReviewerRankService reviewerRankService;

    private BoardService service() {
        return new BoardService(threadRepo, postRepo, flagRepo, unitRepo, userRepo,
                profanityFilterService, reviewerRankService, SECRET);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void signInAs(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(email, "pw", authorities)));
    }

    private User user(String id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setVerifiedStudent(true);
        return user;
    }

    private Unit unit() {
        Unit unit = new Unit();
        unit.setId("unit-1");
        unit.setCode("ISYS1000");
        unit.setName("Introduction to Business Information Systems");
        return unit;
    }

    private BoardThread thread(User author, Instant createdAt) {
        BoardThread thread = new BoardThread();
        thread.setId("thread-1");
        thread.setScope(BoardScope.GENERAL);
        thread.setTitle("Which electives are chill?");
        thread.setBody("Looking for second-year electives with a light workload.");
        thread.setAuthor(author);
        thread.setCreatedAt(createdAt);
        thread.setLastActivityAt(createdAt);
        return thread;
    }

    private BoardPost post(String id, BoardThread thread, User author, Instant createdAt) {
        BoardPost post = new BoardPost();
        post.setId(id);
        post.setThread(thread);
        post.setAuthor(author);
        post.setBody("Reply " + id);
        post.setCreatedAt(createdAt);
        return post;
    }

    // Creating

    @Test
    void createGeneralThread_trimsSavesAndNeverExposesTheAuthor() {
        signInAs(ALICE, "ROLE_USER");
        User alice = user("user-1", ALICE);
        when(userRepo.findByEmail(ALICE)).thenReturn(Optional.of(alice));
        when(profanityFilterService.containsProfanity(anyString())).thenReturn(false);
        when(threadRepo.save(any())).thenAnswer(inv -> {
            BoardThread t = inv.getArgument(0);
            t.setId("thread-1");
            return t;
        });

        BoardThreadDetailDTO dto = service().createGeneralThread("  Chill electives?  ", "  Any ideas?  ");

        ArgumentCaptor<BoardThread> captor = ArgumentCaptor.forClass(BoardThread.class);
        verify(threadRepo).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Chill electives?");
        assertThat(captor.getValue().getBody()).isEqualTo("Any ideas?");
        assertThat(captor.getValue().getScope()).isEqualTo(BoardScope.GENERAL);
        assertThat(captor.getValue().getUnit()).isNull();

        assertThat(dto.author().pseudonym()).isEqualTo(Pseudonym.forUser("user-1", SECRET));
        assertThat(dto.author().verifiedStudent()).isTrue();
        assertThat(dto.ownedByCurrentUser()).isTrue();
        assertThat(dto.canEdit()).isTrue();
        assertThat(dto.toString()).doesNotContain(ALICE).doesNotContain("user-1");
    }

    @Test
    void createUnitThread_attachesTheUnitAndScope() {
        signInAs(ALICE, "ROLE_USER");
        when(unitRepo.findByCode("ISYS1000")).thenReturn(Optional.of(unit()));
        when(userRepo.findByEmail(ALICE)).thenReturn(Optional.of(user("user-1", ALICE)));
        when(profanityFilterService.containsProfanity(anyString())).thenReturn(false);
        when(threadRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BoardThreadDetailDTO dto = service().createUnitThread("isys1000", "Exam tips", "What came up last year?");

        assertThat(dto.scope()).isEqualTo(BoardScope.UNIT);
        assertThat(dto.unitCode()).isEqualTo("ISYS1000");
    }

    @Test
    void createUnitThread_unknownUnitIsNotFound() {
        when(unitRepo.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().createUnitThread("NOPE", "Title", "Body"))
                .isInstanceOf(BoardNotFoundException.class);
        verify(threadRepo, never()).save(any());
    }

    @Test
    void createThread_rejectsProfanityInTitleOrBody() {
        when(profanityFilterService.containsProfanity("Clean title")).thenReturn(false);
        when(profanityFilterService.containsProfanity("bad words")).thenReturn(true);

        assertThatThrownBy(() -> service().createGeneralThread("Clean title", "bad words"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("community standards");
        verify(threadRepo, never()).save(any());
    }

    @Test
    void createThread_rejectsBlankTitleAndOverlongBody() {
        assertThatThrownBy(() -> service().createGeneralThread("   ", "Body"))
                .isInstanceOf(IllegalArgumentException.class);

        when(profanityFilterService.containsProfanity("Title")).thenReturn(false);
        assertThatThrownBy(() -> service().createGeneralThread("Title", "x".repeat(4001)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(threadRepo, never()).save(any());
    }

    // Replying

    @Test
    void createPost_onLockedThreadIsForbidden() {
        signInAs(BOB, "ROLE_USER");
        BoardThread locked = thread(user("user-1", ALICE), Instant.now());
        locked.setLocked(true);
        when(threadRepo.findByIdAndDeletedAtIsNull("thread-1")).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service().createPost("thread-1", "Me too"))
                .isInstanceOf(BoardForbiddenException.class)
                .hasMessageContaining("locked");
        verify(postRepo, never()).save(any());
    }

    @Test
    void createPost_bumpsReplyCountAndActivityAndMarksOp() {
        signInAs(ALICE, "ROLE_USER");
        User alice = user("user-1", ALICE);
        Instant earlier = Instant.now().minus(Duration.ofHours(2));
        BoardThread thread = thread(alice, earlier);
        when(threadRepo.findByIdAndDeletedAtIsNull("thread-1")).thenReturn(Optional.of(thread));
        when(userRepo.findByEmail(ALICE)).thenReturn(Optional.of(alice));
        when(profanityFilterService.containsProfanity(anyString())).thenReturn(false);
        when(postRepo.save(any())).thenAnswer(inv -> {
            BoardPost p = inv.getArgument(0);
            p.setId("post-1");
            return p;
        });
        when(threadRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BoardPostDTO dto = service().createPost("thread-1", "  Following up.  ");

        assertThat(thread.getReplyCount()).isEqualTo(1);
        assertThat(thread.getLastActivityAt()).isAfter(earlier);
        assertThat(dto.body()).isEqualTo("Following up.");
        assertThat(dto.op()).isTrue();
        assertThat(dto.ownedByCurrentUser()).isTrue();
        assertThat(dto.author().pseudonym()).isEqualTo(Pseudonym.forUser("user-1", SECRET));
    }

    @Test
    void createPost_onDeletedThreadIsNotFound() {
        when(threadRepo.findByIdAndDeletedAtIsNull("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().createPost("gone", "Hello"))
                .isInstanceOf(BoardNotFoundException.class);
    }

    // Editing

    @Test
    void updatePost_insideTheWindowSucceedsAndStampsEditedAt() {
        signInAs(ALICE, "ROLE_USER");
        User alice = user("user-1", ALICE);
        BoardThread thread = thread(alice, Instant.now().minus(Duration.ofMinutes(30)));
        BoardPost post = post("post-1", thread, alice, Instant.now().minus(Duration.ofMinutes(5)));
        when(postRepo.findById("post-1")).thenReturn(Optional.of(post));
        when(profanityFilterService.containsProfanity(anyString())).thenReturn(false);
        when(postRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findByEmail(ALICE)).thenReturn(Optional.of(alice));

        BoardPostDTO dto = service().updatePost("post-1", "Corrected reply");

        assertThat(dto.body()).isEqualTo("Corrected reply");
        assertThat(dto.editedAt()).isNotNull();
    }

    @Test
    void updatePost_afterFifteenMinutesIsForbiddenForTheOwner() {
        signInAs(ALICE, "ROLE_USER");
        User alice = user("user-1", ALICE);
        BoardThread thread = thread(alice, Instant.now().minus(Duration.ofHours(1)));
        BoardPost post = post("post-1", thread, alice, Instant.now().minus(Duration.ofMinutes(16)));
        when(postRepo.findById("post-1")).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service().updatePost("post-1", "Too late"))
                .isInstanceOf(BoardForbiddenException.class)
                .hasMessageContaining("15 minutes");
        verify(postRepo, never()).save(any());
    }

    @Test
    void updateThread_afterTheWindowStillWorksForAdmins() {
        signInAs("admin@curtinhonestly.com", "ROLE_ADMIN", "ROLE_USER");
        BoardThread thread = thread(user("user-1", ALICE), Instant.now().minus(Duration.ofDays(3)));
        when(threadRepo.findByIdAndDeletedAtIsNull("thread-1")).thenReturn(Optional.of(thread));
        when(profanityFilterService.containsProfanity(anyString())).thenReturn(false);
        when(threadRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postRepo.findByThread_Id(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(userRepo.findByEmail("admin@curtinhonestly.com")).thenReturn(Optional.empty());

        BoardThreadDetailDTO dto = service().updateThread("thread-1", "Retitled", "Moderated body");

        assertThat(dto.title()).isEqualTo("Retitled");
        assertThat(dto.body()).isEqualTo("Moderated body");
        assertThat(dto.editedAt()).isNotNull();
    }

    // Deleting

    @Test
    void getThread_rendersDeletedPostsAsRemovedPlaceholders() {
        User alice = user("user-1", ALICE);
        User bob = user("user-2", BOB);
        BoardThread thread = thread(alice, Instant.now().minus(Duration.ofHours(1)));
        BoardPost kept = post("post-1", thread, bob, Instant.now().minus(Duration.ofMinutes(50)));
        BoardPost removed = post("post-2", thread, alice, Instant.now().minus(Duration.ofMinutes(40)));
        removed.setDeletedAt(Instant.now().minus(Duration.ofMinutes(10)));
        BoardPost later = post("post-3", thread, bob, Instant.now().minus(Duration.ofMinutes(30)));
        when(threadRepo.findByIdAndDeletedAtIsNull("thread-1")).thenReturn(Optional.of(thread));
        when(postRepo.findByThread_Id(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(kept, removed, later)));

        BoardThreadDetailDTO dto = service().getThread("thread-1", 0, 100);

        assertThat(dto.posts()).hasSize(3);
        BoardPostDTO placeholder = dto.posts().get(1);
        assertThat(placeholder.deleted()).isTrue();
        assertThat(placeholder.body()).isEqualTo(BoardService.REMOVED_PLACEHOLDER);
        assertThat(placeholder.author()).isNull();
        assertThat(placeholder.ownedByCurrentUser()).isFalse();
        assertThat(dto.posts().get(0).deleted()).isFalse();
        assertThat(dto.posts().get(2).author().pseudonym()).isEqualTo(dto.posts().get(0).author().pseudonym());
        // Anonymous reader: nothing is owned and the author is never the email.
        assertThat(dto.ownedByCurrentUser()).isFalse();
        assertThat(dto.toString()).doesNotContain(ALICE).doesNotContain(BOB);
    }

    @Test
    void deletePost_softDeletesAndDecrementsReplyCount() {
        User alice = user("user-1", ALICE);
        BoardThread thread = thread(alice, Instant.now());
        thread.setReplyCount(2);
        BoardPost post = post("post-1", thread, alice, Instant.now());
        when(postRepo.findById("post-1")).thenReturn(Optional.of(post));
        when(postRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(threadRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().deletePost("post-1");

        assertThat(post.getDeletedAt()).isNotNull();
        assertThat(thread.getReplyCount()).isEqualTo(1);
        verify(postRepo, never()).delete(any());
    }

    @Test
    void deleteThread_softDeletesAndThenHidesTheThread() {
        BoardThread thread = thread(user("user-1", ALICE), Instant.now());
        when(threadRepo.findByIdAndDeletedAtIsNull("thread-1"))
                .thenReturn(Optional.of(thread))
                .thenReturn(Optional.empty());
        when(threadRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().deleteThread("thread-1");
        assertThat(thread.getDeletedAt()).isNotNull();

        assertThatThrownBy(() -> service().getThread("thread-1", 0, 100))
                .isInstanceOf(BoardNotFoundException.class);
    }

    // Flagging

    @Test
    void flagPost_isIdempotentPerReporter() {
        signInAs(BOB, "ROLE_USER");
        User bob = user("user-2", BOB);
        BoardThread thread = thread(user("user-1", ALICE), Instant.now());
        BoardPost post = post("post-1", thread, thread.getAuthor(), Instant.now());
        when(postRepo.findById("post-1")).thenReturn(Optional.of(post));
        when(userRepo.findByEmail(BOB)).thenReturn(Optional.of(bob));
        when(flagRepo.existsByReporter_IdAndPost_Id("user-2", "post-1")).thenReturn(false).thenReturn(true);

        service().flagPost("post-1", "Spam");
        service().flagPost("post-1", "Spam again");

        verify(flagRepo).save(any());
    }

    // Anonymity

    @Test
    void anonymisedAuthorsReadAsFormerStudent() {
        BoardThread thread = thread(null, Instant.now());
        when(threadRepo.findByIdAndDeletedAtIsNull("thread-1")).thenReturn(Optional.of(thread));
        when(postRepo.findByThread_Id(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        BoardThreadDetailDTO dto = service().getThread("thread-1", 0, 100);

        assertThat(dto.author().pseudonym()).isEqualTo(Pseudonym.FORMER_STUDENT);
        assertThat(dto.author().verifiedStudent()).isFalse();
        assertThat(dto.author().tier()).isNull();
    }
}

package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Club;
import com.curtinhonestly.backend.domain.ClubEvent;
import com.curtinhonestly.backend.domain.ClubEventKind;
import com.curtinhonestly.backend.domain.ClubEventStatus;
import com.curtinhonestly.backend.domain.ClubMember;
import com.curtinhonestly.backend.domain.ClubMemberRole;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.dto.ClubEventDTO;
import com.curtinhonestly.backend.dto.ClubEventManageDTO;
import com.curtinhonestly.backend.dto.ClubEventUpsertRequest;
import com.curtinhonestly.backend.repo.ClubEventRepo;
import com.curtinhonestly.backend.repo.ClubMemberRepo;
import com.curtinhonestly.backend.repo.ClubRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.service.ClubEventService.EventView;
import com.curtinhonestly.backend.util.UnitTargetRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Matching, the upcoming window, recurring projection and the status
 * transitions are pure and tested directly. The portal paths run through
 * Mockito repos with a real ClubService in front of them, so the membership
 * checks are the production ones.
 */
@ExtendWith(MockitoExtension.class)
class ClubEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T04:00:00Z");

    @Mock ClubEventRepo repo;
    @Mock ClubRepo clubRepo;
    @Mock ClubMemberRepo memberRepo;
    @Mock UnitRepo unitRepo;
    @Mock UserRepo userRepo;
    @Mock UnitResourceLinkService resourceService;
    @Mock ProfanityFilterService profanityFilterService;

    private ClubEventService service() {
        ClubService clubService = new ClubService(clubRepo, memberRepo, repo, userRepo, profanityFilterService);
        return new ClubEventService(repo, clubRepo, unitRepo, userRepo, clubService, resourceService, profanityFilterService);
    }

    // -------------------------------------------------------------- fixtures

    private static Unit unit(String id, String code, Faculty faculty, UnitLevel level) {
        Unit u = new Unit();
        u.setId(id);
        u.setCode(code);
        u.setName(code + " name");
        u.setFaculty(faculty);
        u.setLevel(level);
        return u;
    }

    private static final Unit COMP1000 = unit("u-comp1000", "COMP1000", Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.UNDERGRADUATE);
    private static final Unit COMP2003 = unit("u-comp2003", "COMP2003", Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.UNDERGRADUATE);
    private static final Unit ISAD5000 = unit("u-isad5000", "ISAD5000", Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.POSTGRADUATE);
    private static final Unit MGMT1000 = unit("u-mgmt1000", "MGMT1000", Faculty.BUSINESS_AND_LAW, UnitLevel.UNDERGRADUATE);

    private static EventView view(String id, String title, UnitTargetRule rule, Instant startsAt, Instant endsAt,
                                  boolean recurring, boolean showOnHome, ClubEventStatus status, boolean clubActive) {
        return new EventView(id, "club-1", "ComSSA", "comssa", clubActive, title, null, ClubEventKind.REVISION_SESSION,
                startsAt, endsAt, "Building 314", false, null, recurring, recurring ? "Every week" : null,
                rule, rule.targetUnitId() == null ? null : "COMP1000", null, showOnHome, status, 0);
    }

    private static EventView published(String id, String title, UnitTargetRule rule, Instant startsAt) {
        return view(id, title, rule, startsAt, null, false, false, ClubEventStatus.PUBLISHED, true);
    }

    private static Instant hours(long h) {
        return NOW.plus(Duration.ofHours(h));
    }

    private static List<String> ids(List<EventView> views) {
        return views.stream().map(EventView::id).toList();
    }

    private static Club club(String id, boolean trusted) {
        Club c = new Club();
        c.setId(id);
        c.setName("Club " + id);
        c.setSlug("club-" + id);
        c.setTrusted(trusted);
        c.setActive(true);
        return c;
    }

    private static User user(String id, String email, UserRole... roles) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRoles(new ArrayList<>(List.of(roles)));
        return u;
    }

    private static ClubEvent event(Club club, ClubEventStatus status) {
        ClubEvent e = new ClubEvent();
        e.setId("event-1");
        e.setClub(club);
        e.setTitle("Exam revision");
        e.setKind(ClubEventKind.REVISION_SESSION);
        e.setStartsAt(hours(48));
        e.setStatus(status);
        return e;
    }

    // -------------------------------------------------------------- matching

    @Test
    void unitSpecificEventOnlyShowsOnItsOwnUnit() {
        EventView e = published("e1", "COMP1000 revision", UnitTargetRule.forUnit("u-comp1000"), hours(24));

        assertThat(ids(ClubEventService.forUnit(List.of(e), COMP1000, NOW))).containsExactly("e1");
        assertThat(ClubEventService.forUnit(List.of(e), COMP2003, NOW)).isEmpty();
    }

    @Test
    void prefixRuleMatchesTheStartOfTheCode() {
        EventView e = published("e1", "First-year computing revision",
                UnitTargetRule.rule(List.of("COMP1", "ISAD1"), null, null), hours(24));

        assertThat(ClubEventService.forUnit(List.of(e), COMP1000, NOW)).hasSize(1);
        assertThat(ClubEventService.forUnit(List.of(e), COMP2003, NOW)).isEmpty();
        assertThat(ClubEventService.forUnit(List.of(e), ISAD5000, NOW)).isEmpty();
    }

    @Test
    void facultyAndLevelRulesMustBothHold() {
        EventView faculty = published("f", "Science drop-in",
                UnitTargetRule.rule(List.of(), Faculty.SCIENCE_AND_ENGINEERING, null), hours(24));
        EventView level = published("l", "Postgrad meetup",
                UnitTargetRule.rule(List.of(), null, UnitLevel.POSTGRADUATE), hours(24));
        EventView both = published("b", "UG computing",
                UnitTargetRule.rule(List.of("COMP"), Faculty.SCIENCE_AND_ENGINEERING, UnitLevel.UNDERGRADUATE), hours(24));
        List<EventView> all = List.of(faculty, level, both);

        assertThat(ids(ClubEventService.forUnit(all, COMP1000, NOW))).containsExactlyInAnyOrder("f", "b");
        assertThat(ids(ClubEventService.forUnit(all, ISAD5000, NOW))).containsExactlyInAnyOrder("f", "l");
        assertThat(ClubEventService.forUnit(all, MGMT1000, NOW)).isEmpty();
    }

    @Test
    void ruleWithNoCriteriaIsSiteWide() {
        EventView e = published("e1", "Welcome social", UnitTargetRule.rule(List.of(), null, null), hours(24));

        for (Unit u : List.of(COMP1000, ISAD5000, MGMT1000)) {
            assertThat(ClubEventService.forUnit(List.of(e), u, NOW)).hasSize(1);
        }
        assertThat(ClubEventService.toPublicDTO(e, NOW).scopeLabel()).isEqualTo("All units");
    }

    @Test
    void onlyPublishedEventsOfActiveClubsAreVisible() {
        UnitTargetRule rule = UnitTargetRule.forUnit("u-comp1000");
        EventView draft = view("d", "Draft", rule, hours(24), null, false, true, ClubEventStatus.DRAFT, true);
        EventView pending = view("p", "Pending", rule, hours(24), null, false, true, ClubEventStatus.PENDING, true);
        EventView cancelled = view("c", "Cancelled", rule, hours(24), null, false, true, ClubEventStatus.CANCELLED, true);
        EventView inactiveClub = view("i", "Inactive club", rule, hours(24), null, false, true, ClubEventStatus.PUBLISHED, false);
        EventView ok = view("ok", "Live", rule, hours(24), null, false, true, ClubEventStatus.PUBLISHED, true);

        List<EventView> all = List.of(draft, pending, cancelled, inactiveClub, ok);
        assertThat(ids(ClubEventService.upcomingSorted(all, NOW))).containsExactly("ok");
        assertThat(ids(ClubEventService.forUnit(all, COMP1000, NOW))).containsExactly("ok");
    }

    @Test
    void homeStripOnlyTakesEventsFlaggedForHome() {
        UnitTargetRule rule = UnitTargetRule.rule(List.of("COMP1"), null, null);
        EventView home = view("h", "On home", rule, hours(24), null, false, true, ClubEventStatus.PUBLISHED, true);
        EventView unitOnly = view("u", "Unit only", rule, hours(12), null, false, false, ClubEventStatus.PUBLISHED, true);
        when(repo.findByStatusOrderByStartsAtAsc(ClubEventStatus.PUBLISHED)).thenReturn(List.of());

        // The pure filter the service applies, on top of upcomingSorted.
        List<String> onHome = ClubEventService.upcomingSorted(List.of(home, unitOnly), NOW).stream()
                .filter(EventView::showOnHome)
                .map(EventView::id)
                .toList();
        assertThat(onHome).containsExactly("h");
        // Both still reach the unit page.
        assertThat(ids(ClubEventService.forUnit(List.of(home, unitOnly), COMP1000, NOW))).containsExactly("u", "h");
        // And an empty snapshot yields an empty strip rather than an error.
        assertThat(service().upcomingForHome(4)).isEmpty();
    }

    // ------------------------------------------------------- upcoming window

    @Test
    void oneOffEventStaysUpcomingUntilAnHourAfterItEnds() {
        UnitTargetRule rule = UnitTargetRule.forUnit("u-comp1000");
        EventView endedRecently = view("recent", "Ended 30 min ago", rule, hours(-3), NOW.minus(Duration.ofMinutes(30)),
                false, false, ClubEventStatus.PUBLISHED, true);
        EventView endedLongAgo = view("old", "Ended 2 hours ago", rule, hours(-4), hours(-2),
                false, false, ClubEventStatus.PUBLISHED, true);
        EventView noEndRecent = view("noend", "Started 50 min ago, no end", rule, NOW.minus(Duration.ofMinutes(50)), null,
                false, false, ClubEventStatus.PUBLISHED, true);
        EventView noEndOld = view("noendold", "Started 3 hours ago, no end", rule, hours(-3), null,
                false, false, ClubEventStatus.PUBLISHED, true);

        List<String> upcoming = ids(ClubEventService.upcomingSorted(List.of(endedRecently, endedLongAgo, noEndRecent, noEndOld), NOW));
        assertThat(upcoming).containsExactlyInAnyOrder("recent", "noend");
    }

    @Test
    void upcomingEventsAreSortedSoonestFirstThenByTitle() {
        UnitTargetRule rule = UnitTargetRule.forUnit("u-comp1000");
        EventView later = published("later", "b later", rule, hours(72));
        EventView soonB = published("soonB", "b soon", rule, hours(24));
        EventView soonA = published("soonA", "A soon", rule, hours(24));

        assertThat(ids(ClubEventService.upcomingSorted(List.of(later, soonB, soonA), NOW)))
                .containsExactly("soonA", "soonB", "later");
    }

    @Test
    void recurringEventWithAPastStartStaysUpcomingAndProjectsForwardWeekly() {
        UnitTargetRule rule = UnitTargetRule.forUnit("u-comp1000");
        Instant firstStart = NOW.minus(Duration.ofDays(16)); // two weeks and two days ago
        EventView weekly = view("weekly", "UniPASS COMP1000", rule, firstStart, firstStart.plus(Duration.ofHours(1)),
                true, false, ClubEventStatus.PUBLISHED, true);

        assertThat(weekly.upcoming(NOW)).isTrue();
        Instant next = weekly.nextStart(NOW);
        assertThat(next).isAfter(NOW.minus(Duration.ofHours(1)));
        assertThat(next).isBeforeOrEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(Duration.between(firstStart, next).toDays() % 7).isZero();
        assertThat(next).isEqualTo(firstStart.plus(Duration.ofDays(21)));

        // The public DTO carries both the original start and the projected one.
        ClubEventDTO dto = ClubEventService.toPublicDTO(weekly, NOW);
        assertThat(dto.startsAt()).isEqualTo(firstStart);
        assertThat(dto.nextStartsAt()).isEqualTo(next);
        assertThat(dto.recurring()).isTrue();
    }

    @Test
    void recurringEventWhoseFirstStartIsAheadUsesItsOwnStart() {
        UnitTargetRule rule = UnitTargetRule.forUnit("u-comp1000");
        EventView weekly = view("weekly", "UniPASS", rule, hours(48), hours(49), true, false, ClubEventStatus.PUBLISHED, true);
        assertThat(weekly.nextStart(NOW)).isEqualTo(hours(48));
    }

    @Test
    void recurringEventsSortByTheirProjectedNextStart() {
        UnitTargetRule rule = UnitTargetRule.forUnit("u-comp1000");
        // Started 6 days ago and runs weekly: next occurrence is tomorrow.
        EventView weekly = view("weekly", "Weekly", rule, hours(-24 * 6), hours(-24 * 6 + 1), true, false, ClubEventStatus.PUBLISHED, true);
        EventView inThreeDays = published("three", "Three days", rule, hours(72));
        EventView inTwoHours = published("two", "Two hours", rule, hours(2));

        assertThat(ids(ClubEventService.upcomingSorted(List.of(weekly, inThreeDays, inTwoHours), NOW)))
                .containsExactly("two", "weekly", "three");
    }

    // ------------------------------------------------------ transitions

    @Test
    void publishingFromATrustedClubGoesLiveImmediately() {
        Club trusted = club("c1", true);
        ClubEvent e = event(trusted, ClubEventStatus.DRAFT);
        e.setRejectionReason("old reason");

        ClubEventService.applyPublish(e, trusted, NOW);

        assertThat(e.getStatus()).isEqualTo(ClubEventStatus.PUBLISHED);
        assertThat(e.getPublishedAt()).isEqualTo(NOW);
        assertThat(e.getRejectionReason()).isNull();
    }

    @Test
    void publishingFromAnUntrustedClubWaitsForAnAdmin() {
        Club untrusted = club("c2", false);
        ClubEvent e = event(untrusted, ClubEventStatus.DRAFT);

        ClubEventService.applyPublish(e, untrusted, NOW);

        assertThat(e.getStatus()).isEqualTo(ClubEventStatus.PENDING);
        assertThat(e.getPublishedAt()).isNull();
    }

    @Test
    void rejectedEventsCanBePublishedAgainButLiveOnesCannot() {
        Club trusted = club("c1", true);
        ClubEvent rejected = event(trusted, ClubEventStatus.REJECTED);
        ClubEventService.applyPublish(rejected, trusted, NOW);
        assertThat(rejected.getStatus()).isEqualTo(ClubEventStatus.PUBLISHED);

        ClubEvent live = event(trusted, ClubEventStatus.PUBLISHED);
        assertThatThrownBy(() -> ClubEventService.applyPublish(live, trusted, NOW))
                .isInstanceOf(IllegalStateException.class);
        ClubEvent pending = event(trusted, ClubEventStatus.PENDING);
        assertThatThrownBy(() -> ClubEventService.applyPublish(pending, trusted, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelOnlyAppliesToPublishedOrPendingEvents() {
        Club c = club("c1", true);
        ClubEvent live = event(c, ClubEventStatus.PUBLISHED);
        ClubEventService.applyCancel(live, NOW);
        assertThat(live.getStatus()).isEqualTo(ClubEventStatus.CANCELLED);

        ClubEvent pending = event(c, ClubEventStatus.PENDING);
        ClubEventService.applyCancel(pending, NOW);
        assertThat(pending.getStatus()).isEqualTo(ClubEventStatus.CANCELLED);

        ClubEvent draft = event(c, ClubEventStatus.DRAFT);
        assertThatThrownBy(() -> ClubEventService.applyCancel(draft, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adminApproveAndRejectTransitions() {
        Club c = club("c2", false);
        ClubEvent pending = event(c, ClubEventStatus.PENDING);
        ClubEventService.applyApprove(pending, NOW);
        assertThat(pending.getStatus()).isEqualTo(ClubEventStatus.PUBLISHED);
        assertThat(pending.getPublishedAt()).isEqualTo(NOW);

        ClubEvent draft = event(c, ClubEventStatus.DRAFT);
        assertThatThrownBy(() -> ClubEventService.applyApprove(draft, NOW)).isInstanceOf(IllegalStateException.class);

        ClubEvent toReject = event(c, ClubEventStatus.PENDING);
        assertThatThrownBy(() -> ClubEventService.applyReject(toReject, "  ", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
        ClubEventService.applyReject(toReject, "Wrong unit codes", NOW);
        assertThat(toReject.getStatus()).isEqualTo(ClubEventStatus.REJECTED);
        assertThat(toReject.getRejectionReason()).isEqualTo("Wrong unit codes");
        assertThat(toReject.getPublishedAt()).isNull();

        ClubEvent cancelled = event(c, ClubEventStatus.CANCELLED);
        assertThatThrownBy(() -> ClubEventService.applyReject(cancelled, "x", NOW)).isInstanceOf(IllegalStateException.class);
    }

    // -------------------------------------------------------- portal paths

    private ClubEventUpsertRequest request(String title) {
        return new ClubEventUpsertRequest(title, "Bring your notes", "REVISION_SESSION", hours(48), hours(50),
                "Building 314", false, "comssa.org.au/events", false, null, null, "COMP1, ISAD1", null, null, true);
    }

    @Test
    void portalCreateMakesADraftForAMember() {
        Club trusted = club("c1", true);
        User member = user("u1", "legend@curtinhonestly.test", UserRole.ROLE_USER, UserRole.ROLE_CLUB);
        ClubMember membership = new ClubMember();
        membership.setClub(trusted);
        membership.setUser(member);
        membership.setRole(ClubMemberRole.EDITOR);
        when(userRepo.findByEmail("legend@curtinhonestly.test")).thenReturn(Optional.of(member));
        when(clubRepo.findById("c1")).thenReturn(Optional.of(trusted));
        when(memberRepo.findByClub_IdAndUser_Id("c1", "u1")).thenReturn(Optional.of(membership));
        when(profanityFilterService.containsProfanity(any())).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> {
            ClubEvent saved = inv.getArgument(0);
            saved.setId("new-id");
            return saved;
        });

        ClubEventManageDTO dto = service().portalCreate("c1", "legend@curtinhonestly.test", request("  First-year exam revision "));

        ArgumentCaptor<ClubEvent> captor = ArgumentCaptor.forClass(ClubEvent.class);
        verify(repo).save(captor.capture());
        ClubEvent saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ClubEventStatus.DRAFT);
        assertThat(saved.getTitle()).isEqualTo("First-year exam revision");
        assertThat(saved.getCodePrefixes()).isEqualTo("COMP1,ISAD1");
        assertThat(saved.getLink()).isEqualTo("https://comssa.org.au/events");
        assertThat(saved.isShowOnHome()).isTrue();
        assertThat(saved.getCreatedBy()).isSameAs(member);
        assertThat(dto.scopeLabel()).isEqualTo("All COMP1 and ISAD1 units");
        assertThat(dto.status()).isEqualTo("DRAFT");
        // Members do not see who created what; only admins do.
        assertThat(dto.createdByEmail()).isNull();
    }

    @Test
    void nonMembersGetForbiddenBeforeAnythingIsSaved() {
        Club trusted = club("c1", true);
        User outsider = user("u9", "someone@student.curtin.edu.au", UserRole.ROLE_USER);
        when(userRepo.findByEmail("someone@student.curtin.edu.au")).thenReturn(Optional.of(outsider));
        when(clubRepo.findById("c1")).thenReturn(Optional.of(trusted));
        when(memberRepo.findByClub_IdAndUser_Id("c1", "u9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().portalCreate("c1", "someone@student.curtin.edu.au", request("x")))
                .isInstanceOf(ClubForbiddenException.class);
        assertThatThrownBy(() -> service().portalEvents("c1", "someone@student.curtin.edu.au"))
                .isInstanceOf(ClubForbiddenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void portalCannotReachAnotherClubsEvent() {
        Club mine = club("c1", true);
        Club theirs = club("c2", true);
        User member = user("u1", "legend@curtinhonestly.test", UserRole.ROLE_USER, UserRole.ROLE_CLUB);
        ClubMember membership = new ClubMember();
        membership.setClub(mine);
        membership.setUser(member);
        membership.setRole(ClubMemberRole.OWNER);
        when(userRepo.findByEmail("legend@curtinhonestly.test")).thenReturn(Optional.of(member));
        when(clubRepo.findById("c1")).thenReturn(Optional.of(mine));
        when(memberRepo.findByClub_IdAndUser_Id("c1", "u1")).thenReturn(Optional.of(membership));
        ClubEvent theirEvent = event(theirs, ClubEventStatus.DRAFT);
        when(repo.findWithDetailsById("event-1")).thenReturn(Optional.of(theirEvent));

        assertThatThrownBy(() -> service().portalPublish("c1", "legend@curtinhonestly.test", "event-1"))
                .isInstanceOf(ClubNotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void untrustedClubEditingALiveEventSendsItBackToPending() {
        Club untrusted = club("c2", false);
        User member = user("u1", "regular@curtinhonestly.test", UserRole.ROLE_USER, UserRole.ROLE_CLUB);
        ClubMember membership = new ClubMember();
        membership.setClub(untrusted);
        membership.setUser(member);
        membership.setRole(ClubMemberRole.EDITOR);
        when(userRepo.findByEmail("regular@curtinhonestly.test")).thenReturn(Optional.of(member));
        when(clubRepo.findById("c2")).thenReturn(Optional.of(untrusted));
        when(memberRepo.findByClub_IdAndUser_Id("c2", "u1")).thenReturn(Optional.of(membership));
        when(profanityFilterService.containsProfanity(any())).thenReturn(false);
        ClubEvent live = event(untrusted, ClubEventStatus.PUBLISHED);
        live.setPublishedAt(hours(-24));
        when(repo.findWithDetailsById("event-1")).thenReturn(Optional.of(live));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ClubEventManageDTO dto = service().portalUpdate("c2", "regular@curtinhonestly.test", "event-1", request("Renamed"));

        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.publishedAt()).isNull();
        assertThat(dto.title()).isEqualTo("Renamed");
    }

    @Test
    void upsertValidatesTimesAndRecurrenceAndProfanity() {
        ClubEventService service = service();
        ClubEvent e = new ClubEvent();
        when(profanityFilterService.containsProfanity(any())).thenReturn(false);

        assertThatThrownBy(() -> service.applyUpsert(e, new ClubEventUpsertRequest("t", null, "SOCIAL", null, null,
                null, false, null, false, null, null, null, null, null, false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("start time");
        assertThatThrownBy(() -> service.applyUpsert(e, new ClubEventUpsertRequest("t", null, "SOCIAL", hours(2), hours(1),
                null, false, null, false, null, null, null, null, null, false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("end time");
        assertThatThrownBy(() -> service.applyUpsert(e, new ClubEventUpsertRequest("t", null, "SOCIAL", hours(2), null,
                null, false, null, true, "  ", null, null, null, null, false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("recurring");
        assertThatThrownBy(() -> service.applyUpsert(e, new ClubEventUpsertRequest("t", null, "SOCIAL", hours(2), null,
                null, false, "javascript:alert(1)", false, null, null, null, null, null, false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyUpsert(e, new ClubEventUpsertRequest("t", null, "PARTY", hours(2), null,
                null, false, null, false, null, null, null, null, null, false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown event kind");

        when(profanityFilterService.containsProfanity("Rude title")).thenReturn(true);
        assertThatThrownBy(() -> service.applyUpsert(e, new ClubEventUpsertRequest("Rude title", null, "SOCIAL", hours(2), null,
                null, false, null, false, null, null, null, null, null, false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("community standards");
    }

    @Test
    void upsertWithAUnitCodeTargetsThatUnitAndClearsTheRule() {
        ClubEventService service = service();
        when(profanityFilterService.containsProfanity(any())).thenReturn(false);
        when(unitRepo.findByCode("COMP1000")).thenReturn(Optional.of(COMP1000));
        ClubEvent e = new ClubEvent();
        e.setCodePrefixes("COMP");
        e.setFaculty(Faculty.HUMANITIES);

        service.applyUpsert(e, new ClubEventUpsertRequest("UniPASS COMP1000", null, "TUTORING", hours(2), hours(3),
                null, true, null, true, "Every Tuesday, weeks 2 to 12", " comp1000 ", "COMP", "HUMANITIES", null, false));

        assertThat(e.getTargetUnit()).isSameAs(COMP1000);
        assertThat(e.getCodePrefixes()).isNull();
        assertThat(e.getFaculty()).isNull();
        assertThat(e.isOnline()).isTrue();
        assertThat(e.isRecurring()).isTrue();
        assertThat(e.rule().scopeLabel()).isEqualTo("This unit");
    }
}

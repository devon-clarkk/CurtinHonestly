package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Club;
import com.curtinhonestly.backend.domain.ClubEvent;
import com.curtinhonestly.backend.domain.ClubEventKind;
import com.curtinhonestly.backend.domain.ClubEventStatus;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.AdminUnitResourceOptionsDTO;
import com.curtinhonestly.backend.dto.AdminUnitResourcePreviewDTO;
import com.curtinhonestly.backend.dto.ClubEventDTO;
import com.curtinhonestly.backend.dto.ClubEventManageDTO;
import com.curtinhonestly.backend.dto.ClubEventOptionsDTO;
import com.curtinhonestly.backend.dto.ClubEventUpsertRequest;
import com.curtinhonestly.backend.dto.ClubProfileDTO;
import com.curtinhonestly.backend.dto.ClubSummaryDTO;
import com.curtinhonestly.backend.repo.ClubEventRepo;
import com.curtinhonestly.backend.repo.ClubRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.util.EmailNormalizer;
import com.curtinhonestly.backend.util.SafeUrl;
import com.curtinhonestly.backend.util.UnitTargetRule;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Club study sessions and events: the public listings, the club portal's
 * create/publish/cancel flow and the admin moderation queue.
 *
 * Visibility: only PUBLISHED events of active clubs are ever public. An event
 * is "upcoming" while its end (or its start, when it has no end) is less than
 * an hour in the past; a recurring event stays upcoming for as long as it is
 * published, with its display date projected forward a week at a time from
 * its first start. Unit targeting is {@link UnitTargetRule}, the same rule
 * shape as unit resources.
 *
 * The published set is small and read on the home page and every unit page,
 * so it is loaded once into an immutable snapshot that lives for five minutes
 * or until any write, whichever comes first.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(rollbackOn = Exception.class)
public class ClubEventService {

    static final Duration SNAPSHOT_TTL = Duration.ofMinutes(5);
    /** How long after an event ends it still counts as upcoming, so a running session does not vanish. */
    static final Duration GRACE = Duration.ofHours(1);
    public static final int DEFAULT_HOME_LIMIT = 4;
    public static final int MAX_HOME_LIMIT = 12;
    public static final int MAX_PAGE_SIZE = 100;

    private final ClubEventRepo repo;
    private final ClubRepo clubRepo;
    private final UnitRepo unitRepo;
    private final UserRepo userRepo;
    private final ClubService clubService;
    private final UnitResourceLinkService resourceService;
    private final ProfanityFilterService profanityFilterService;

    /**
     * An immutable, detached view of one event: everything matching and the
     * public DTO need, and nothing that could trip a lazy load later.
     */
    public record EventView(
            String id,
            String clubId,
            String clubName,
            String clubSlug,
            boolean clubActive,
            String title,
            String description,
            ClubEventKind kind,
            Instant startsAt,
            Instant endsAt,
            String location,
            boolean online,
            String link,
            boolean recurring,
            String recurrenceNote,
            UnitTargetRule rule,
            String targetUnitCode,
            String targetUnitName,
            boolean showOnHome,
            ClubEventStatus status,
            int viewCount
    ) {
        public static EventView from(ClubEvent e) {
            Club club = e.getClub();
            Unit target = e.getTargetUnit();
            return new EventView(
                    e.getId(),
                    club.getId(), club.getName(), club.getSlug(), club.isActive(),
                    e.getTitle(), e.getDescription(), e.getKind(),
                    e.getStartsAt(), e.getEndsAt(),
                    e.getLocation(), e.isOnline(), e.getLink(),
                    e.isRecurring(), e.getRecurrenceNote(),
                    e.rule(),
                    target == null ? null : target.getCode(),
                    target == null ? null : target.getName(),
                    e.isShowOnHome(), e.getStatus(), e.getViewCount());
        }

        /** Published and belonging to an active club: the only rows the public site shows. */
        public boolean visible() {
            return status == ClubEventStatus.PUBLISHED && clubActive;
        }

        /** The end, or the start when there is no end. */
        public Instant lastMoment() {
            return endsAt != null ? endsAt : startsAt;
        }

        /** Still worth listing at {@code now}: not over by more than an hour, or recurring. */
        public boolean upcoming(Instant now) {
            return recurring || lastMoment().isAfter(now.minus(GRACE));
        }

        /**
         * The start to show and sort by. A one-off event, or a recurring one whose
         * first occurrence is still ahead, uses its own start. A recurring event
         * whose first occurrence has passed is projected forward a week at a time
         * until an occurrence is not yet over; the recurrence note carries the real
         * cadence, this is only the "next" date the listing needs.
         */
        public Instant nextStart(Instant now) {
            Instant cutoff = now.minus(GRACE);
            if (!recurring || lastMoment().isAfter(cutoff)) {
                return startsAt;
            }
            Duration length = endsAt != null && endsAt.isAfter(startsAt) ? Duration.between(startsAt, endsAt) : Duration.ZERO;
            long daysBehind = ChronoUnit.DAYS.between(startsAt.plus(length), cutoff);
            long weeks = Math.max(1, daysBehind / 7 + 1);
            return startsAt.plus(Duration.ofDays(7 * weeks));
        }

        public boolean matchesUnit(Unit unit) {
            return rule.matches(unit.getId(), unit.getCode(), unit.getFaculty(), unit.getLevel());
        }
    }

    private record Snapshot(List<EventView> events, Instant loadedAt) {}

    private volatile Snapshot snapshot;

    // ---------------------------------------------------------------- public

    /** Home page strip: published, flagged for home, upcoming, soonest first. */
    public List<ClubEventDTO> upcomingForHome(Integer limit) {
        int n = limit == null || limit < 1 ? DEFAULT_HOME_LIMIT : Math.min(limit, MAX_HOME_LIMIT);
        Instant now = Instant.now();
        return upcomingSorted(published(), now).stream()
                .filter(EventView::showOnHome)
                .limit(n)
                .map(v -> toPublicDTO(v, now))
                .toList();
    }

    /** Empty when the unit code is unknown (the controller turns that into a 404). */
    public Optional<List<ClubEventDTO>> forUnitCode(String code) {
        Instant now = Instant.now();
        return unitRepo.findByCode(code)
                .map(unit -> forUnit(published(), unit, now).stream().map(v -> toPublicDTO(v, now)).toList());
    }

    /** The /events page: every upcoming published event, optionally one club or one kind, soonest first. */
    public Page<ClubEventDTO> list(int page, int size, String clubSlug, String kind) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        Instant now = Instant.now();
        ClubEventKind wantedKind = kind == null || kind.isBlank() ? null : ClubEventKind.parse(kind);
        String wantedSlug = clubSlug == null || clubSlug.isBlank() ? null : clubSlug.trim().toLowerCase(Locale.ROOT);

        List<EventView> all = upcomingSorted(published(), now).stream()
                .filter(v -> wantedKind == null || v.kind() == wantedKind)
                .filter(v -> wantedSlug == null || wantedSlug.equals(v.clubSlug()))
                .toList();
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        List<ClubEventDTO> content = all.subList(from, to).stream().map(v -> toPublicDTO(v, now)).toList();
        return new PageImpl<>(content, PageRequest.of(safePage, safeSize), all.size());
    }

    /** One published event, past or upcoming. 404 for drafts, pending, cancelled and unknown ids. */
    public ClubEventDTO detail(String id) {
        Instant now = Instant.now();
        return published().stream()
                .filter(v -> v.id().equals(id))
                .findFirst()
                .map(v -> toPublicDTO(v, now))
                .orElseThrow(() -> new ClubNotFoundException("Event not found."));
    }

    /** Counts a view on a published event. False when the id is unknown or not published. */
    public boolean recordView(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return repo.incrementViews(id) > 0;
    }

    /** Active clubs, alphabetical, each with its number of upcoming published events. */
    public List<ClubSummaryDTO> directory() {
        Instant now = Instant.now();
        Map<String, Integer> counts = new HashMap<>();
        for (EventView v : upcomingSorted(published(), now)) {
            counts.merge(v.clubId(), 1, Integer::sum);
        }
        return clubRepo.findByActiveTrueOrderByNameAsc().stream()
                .map(c -> new ClubSummaryDTO(c.getId(), c.getName(), c.getSlug(), c.getDescription(),
                        c.getWebsiteUrl(), c.getLogoUrl(), counts.getOrDefault(c.getId(), 0)))
                .toList();
    }

    /** A club's public page. 404 for unknown or inactive clubs. */
    public ClubProfileDTO profile(String slug) {
        Club club = clubRepo.findBySlugAndActiveTrue(slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ClubNotFoundException("Club not found."));
        Instant now = Instant.now();
        List<ClubEventDTO> events = upcomingSorted(published(), now).stream()
                .filter(v -> v.clubId().equals(club.getId()))
                .map(v -> toPublicDTO(v, now))
                .toList();
        return new ClubProfileDTO(club.getId(), club.getName(), club.getSlug(), club.getDescription(),
                club.getWebsiteUrl(), club.getLogoUrl(), club.getContactEmail(), events);
    }

    // ---------------------------------------------------------- pure listing

    /** Visible and upcoming, soonest projected start first, title as the tiebreak. */
    public static List<EventView> upcomingSorted(List<EventView> views, Instant now) {
        return views.stream()
                .filter(EventView::visible)
                .filter(v -> v.upcoming(now))
                .sorted(Comparator.comparing((EventView v) -> v.nextStart(now))
                        .thenComparing(v -> v.title().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** The upcoming events whose targeting covers the unit. */
    public static List<EventView> forUnit(List<EventView> views, Unit unit, Instant now) {
        return upcomingSorted(views, now).stream()
                .filter(v -> v.matchesUnit(unit))
                .toList();
    }

    public static ClubEventDTO toPublicDTO(EventView v, Instant now) {
        return new ClubEventDTO(
                v.id(), v.clubId(), v.clubName(), v.clubSlug(),
                v.title(), v.description(), v.kind().name(), v.kind().getDisplayName(),
                v.startsAt(), v.endsAt(), v.nextStart(now),
                v.location(), v.online(), v.link(), v.recurring(), v.recurrenceNote(),
                v.rule().scopeLabel(), v.targetUnitCode(), v.targetUnitName(),
                v.showOnHome(), v.viewCount());
    }

    // ---------------------------------------------------------------- portal

    public List<ClubEventManageDTO> portalEvents(String clubId, String email) {
        ClubService.Access access = clubService.requireMember(clubId, email);
        return repo.findByClub_IdOrderByStartsAtDesc(clubId).stream()
                .map(e -> toManageDTO(e, access.admin()))
                .toList();
    }

    /** A member drafts an event. Nothing is public until it is published. */
    public ClubEventManageDTO portalCreate(String clubId, String email, ClubEventUpsertRequest request) {
        ClubService.Access access = clubService.requireMember(clubId, email);
        ClubEvent event = new ClubEvent();
        event.setClub(access.club());
        event.setCreatedBy(access.user());
        applyUpsert(event, request);
        event.setStatus(ClubEventStatus.DRAFT);
        ClubEvent saved = repo.save(event);
        log.info("User {} drafted event {} for club {}", access.user().getId(), saved.getId(), clubId);
        return toManageDTO(saved, access.admin());
    }

    /**
     * Edits keep the current status, with one exception: an untrusted club
     * editing a published event sends it back to PENDING, because what an admin
     * approved is no longer what is on the page.
     */
    public ClubEventManageDTO portalUpdate(String clubId, String email, String eventId, ClubEventUpsertRequest request) {
        ClubService.Access access = clubService.requireMember(clubId, email);
        ClubEvent event = findForClub(eventId, clubId);
        applyUpsert(event, request);
        if (!access.club().isTrusted() && event.getStatus() == ClubEventStatus.PUBLISHED) {
            event.setStatus(ClubEventStatus.PENDING);
            event.setPublishedAt(null);
        }
        ClubEvent saved = repo.save(event);
        invalidate();
        return toManageDTO(saved, access.admin());
    }

    /** Trusted club: PUBLISHED now. Untrusted club: PENDING until an admin approves. */
    public ClubEventManageDTO portalPublish(String clubId, String email, String eventId) {
        ClubService.Access access = clubService.requireMember(clubId, email);
        ClubEvent event = findForClub(eventId, clubId);
        applyPublish(event, access.club(), Instant.now());
        ClubEvent saved = repo.save(event);
        invalidate();
        log.info("User {} published event {} ({}) for club {}", access.user().getId(), eventId, saved.getStatus(), clubId);
        return toManageDTO(saved, access.admin());
    }

    public ClubEventManageDTO portalCancel(String clubId, String email, String eventId) {
        ClubService.Access access = clubService.requireMember(clubId, email);
        ClubEvent event = findForClub(eventId, clubId);
        applyCancel(event, Instant.now());
        ClubEvent saved = repo.save(event);
        invalidate();
        return toManageDTO(saved, access.admin());
    }

    /** Drafts only. Anything that was ever public is cancelled instead, so the record stays. */
    public void portalDelete(String clubId, String email, String eventId) {
        clubService.requireMember(clubId, email);
        ClubEvent event = findForClub(eventId, clubId);
        if (event.getStatus() != ClubEventStatus.DRAFT) {
            throw new IllegalStateException("Only drafts can be deleted. Cancel a published or pending event instead.");
        }
        repo.delete(event);
    }

    /** Dry run of a targeting rule: how many units it matches and a sample of their codes. */
    public AdminUnitResourcePreviewDTO preview(String codePrefixes, String faculty, String level, String unitCode) {
        return resourceService.preview(codePrefixes, faculty, level, unitCode);
    }

    public ClubEventOptionsDTO options() {
        return new ClubEventOptionsDTO(
                Arrays.stream(ClubEventKind.values())
                        .map(k -> new AdminUnitResourceOptionsDTO.Option(k.name(), k.getDisplayName())).toList(),
                Arrays.stream(ClubEventStatus.values())
                        .map(s -> new AdminUnitResourceOptionsDTO.Option(s.name(), s.getDisplayName())).toList(),
                Arrays.stream(Faculty.values())
                        .map(f -> new AdminUnitResourceOptionsDTO.Option(f.name(), f.getDisplayName())).toList(),
                Arrays.stream(UnitLevel.values())
                        .map(l -> new AdminUnitResourceOptionsDTO.Option(l.name(), l.getDisplayName())).toList());
    }

    // ----------------------------------------------------------------- admin

    public List<ClubEventManageDTO> adminList(ClubEventStatus status, String clubId) {
        List<ClubEvent> rows = status == null
                ? repo.findAllByOrderByCreatedAtDesc()
                : repo.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream()
                .filter(e -> clubId == null || clubId.isBlank() || e.getClub().getId().equals(clubId))
                .map(e -> toManageDTO(e, true))
                .toList();
    }

    /** Admins post on behalf of any club; the event is live immediately. */
    public ClubEventManageDTO adminCreate(String clubId, ClubEventUpsertRequest request) {
        Club club = clubRepo.findById(clubId)
                .orElseThrow(() -> new ClubNotFoundException("Club not found."));
        ClubEvent event = new ClubEvent();
        event.setClub(club);
        currentUser().ifPresent(event::setCreatedBy);
        applyUpsert(event, request);
        event.setStatus(ClubEventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        ClubEvent saved = repo.save(event);
        invalidate();
        log.info("Admin created event {} for club {}", saved.getId(), clubId);
        return toManageDTO(saved, true);
    }

    public ClubEventManageDTO adminUpdate(String eventId, ClubEventUpsertRequest request) {
        ClubEvent event = find(eventId);
        applyUpsert(event, request);
        ClubEvent saved = repo.save(event);
        invalidate();
        return toManageDTO(saved, true);
    }

    public ClubEventManageDTO approve(String eventId) {
        ClubEvent event = find(eventId);
        applyApprove(event, Instant.now());
        ClubEvent saved = repo.save(event);
        invalidate();
        log.info("Admin approved event {} ({})", eventId, saved.getTitle());
        return toManageDTO(saved, true);
    }

    public ClubEventManageDTO reject(String eventId, String reason) {
        ClubEvent event = find(eventId);
        applyReject(event, reason, Instant.now());
        ClubEvent saved = repo.save(event);
        invalidate();
        log.info("Admin rejected event {} ({})", eventId, saved.getTitle());
        return toManageDTO(saved, true);
    }

    public ClubEventManageDTO adminCancel(String eventId) {
        ClubEvent event = find(eventId);
        applyCancel(event, Instant.now());
        ClubEvent saved = repo.save(event);
        invalidate();
        return toManageDTO(saved, true);
    }

    public void adminDelete(String eventId) {
        repo.deleteById(eventId);
        invalidate();
    }

    // ------------------------------------------------------ pure transitions

    /** Publishing: trusted clubs go live, others queue for an admin. Clears any earlier rejection. */
    static void applyPublish(ClubEvent event, Club club, Instant now) {
        ClubEventStatus current = event.getStatus();
        if (current == ClubEventStatus.PUBLISHED) {
            throw new IllegalStateException("This event is already published.");
        }
        if (current == ClubEventStatus.PENDING) {
            throw new IllegalStateException("This event is already waiting for admin approval.");
        }
        event.setRejectionReason(null);
        if (club.isTrusted()) {
            event.setStatus(ClubEventStatus.PUBLISHED);
            event.setPublishedAt(now);
        } else {
            event.setStatus(ClubEventStatus.PENDING);
            event.setPublishedAt(null);
        }
        event.setUpdatedAt(now);
    }

    static void applyCancel(ClubEvent event, Instant now) {
        ClubEventStatus current = event.getStatus();
        if (current != ClubEventStatus.PUBLISHED && current != ClubEventStatus.PENDING) {
            throw new IllegalStateException("Only published or pending events can be cancelled.");
        }
        event.setStatus(ClubEventStatus.CANCELLED);
        event.setUpdatedAt(now);
    }

    static void applyApprove(ClubEvent event, Instant now) {
        if (event.getStatus() != ClubEventStatus.PENDING) {
            throw new IllegalStateException("Only pending events can be approved.");
        }
        event.setStatus(ClubEventStatus.PUBLISHED);
        event.setPublishedAt(now);
        event.setRejectionReason(null);
        event.setUpdatedAt(now);
    }

    /** Rejecting a pending event, or taking a published one down with a reason the club can see. */
    static void applyReject(ClubEvent event, String reason, Instant now) {
        ClubEventStatus current = event.getStatus();
        if (current != ClubEventStatus.PENDING && current != ClubEventStatus.PUBLISHED) {
            throw new IllegalStateException("Only pending or published events can be rejected.");
        }
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Give the club a reason so they know what to change.");
        }
        if (text.length() > ClubEvent.MAX_REJECTION_REASON) {
            throw new IllegalArgumentException("The reason must be " + ClubEvent.MAX_REJECTION_REASON + " characters or fewer.");
        }
        event.setStatus(ClubEventStatus.REJECTED);
        event.setRejectionReason(text);
        event.setPublishedAt(null);
        event.setUpdatedAt(now);
    }

    // ---------------------------------------------------------------- helpers

    private List<EventView> published() {
        Snapshot current = snapshot;
        Instant now = Instant.now();
        if (current == null || current.loadedAt().plus(SNAPSHOT_TTL).isBefore(now)) {
            List<EventView> events = repo.findByStatusOrderByStartsAtAsc(ClubEventStatus.PUBLISHED).stream()
                    .map(EventView::from)
                    .toList();
            current = new Snapshot(events, now);
            snapshot = current;
        }
        return current.events();
    }

    /** Drops the cached published set; the next public read reloads it. */
    public void invalidate() {
        snapshot = null;
    }

    private ClubEvent find(String eventId) {
        return repo.findWithDetailsById(eventId)
                .orElseThrow(() -> new ClubNotFoundException("Event not found."));
    }

    /** The event, and only if it belongs to the club in the URL: a member of club A cannot reach club B's rows. */
    private ClubEvent findForClub(String eventId, String clubId) {
        ClubEvent event = find(eventId);
        if (!event.getClub().getId().equals(clubId)) {
            throw new ClubNotFoundException("Event not found.");
        }
        return event;
    }

    /** Validates and copies every editable field. Shared by the portal and the admin app. */
    void applyUpsert(ClubEvent event, ClubEventUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A request body is required.");
        }
        String title = requireText(request.title(), "A title", ClubEvent.MAX_TITLE);
        String description = optionalText(request.description(), "The description", ClubEvent.MAX_DESCRIPTION);
        String location = optionalText(request.location(), "The location", ClubEvent.MAX_LOCATION);
        String recurrenceNote = optionalText(request.recurrenceNote(), "The recurrence note", ClubEvent.MAX_RECURRENCE_NOTE);
        for (String text : new String[]{title, description, location, recurrenceNote}) {
            if (profanityFilterService.containsProfanity(text)) {
                throw new IllegalArgumentException("The event contains language that violates our community standards.");
            }
        }
        if (request.startsAt() == null) {
            throw new IllegalArgumentException("A start time is required.");
        }
        if (request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw new IllegalArgumentException("The end time must be after the start time.");
        }
        boolean recurring = Boolean.TRUE.equals(request.recurring());
        if (recurring && recurrenceNote == null) {
            throw new IllegalArgumentException("Say how often a recurring event runs, e.g. \"Every Tuesday, weeks 2 to 12\".");
        }

        event.setTitle(title);
        event.setDescription(description);
        event.setKind(ClubEventKind.parse(request.kind()));
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setLocation(location);
        event.setOnline(Boolean.TRUE.equals(request.online()));
        event.setLink(request.link() == null || request.link().isBlank() ? null : SafeUrl.normalise(request.link()));
        event.setRecurring(recurring);
        event.setRecurrenceNote(recurring ? recurrenceNote : null);
        event.setShowOnHome(Boolean.TRUE.equals(request.showOnHome()));
        event.setUpdatedAt(Instant.now());

        if (request.unitCode() != null && !request.unitCode().isBlank()) {
            String code = request.unitCode().trim().toUpperCase(Locale.ROOT);
            Unit unit = unitRepo.findByCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + code));
            event.setTargetUnit(unit);
            event.setCodePrefixes(null);
            event.setFaculty(null);
            event.setLevel(null);
            return;
        }
        List<String> prefixes = UnitResourceLinkService.normalisePrefixes(request.codePrefixes());
        event.setTargetUnit(null);
        event.setCodePrefixes(UnitTargetRule.joinForStorage(prefixes));
        event.setFaculty(UnitResourceLinkService.parseFaculty(request.faculty()));
        event.setLevel(UnitResourceLinkService.parseLevel(request.level()));
    }

    private static String requireText(String raw, String what, int max) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(what + " is required.");
        }
        if (text.length() > max) {
            throw new IllegalArgumentException(what + " must be " + max + " characters or fewer.");
        }
        return text;
    }

    private static String optionalText(String raw, String what, int max) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > max) {
            throw new IllegalArgumentException(what + " must be " + max + " characters or fewer.");
        }
        return text;
    }

    private Optional<User> currentUser() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        return userRepo.findByEmail(EmailNormalizer.normalize(name));
    }

    static ClubEventManageDTO toManageDTO(ClubEvent e, boolean includeCreator) {
        Club club = e.getClub();
        Unit target = e.getTargetUnit();
        UnitTargetRule rule = e.rule();
        User creator = includeCreator ? e.getCreatedBy() : null;
        return new ClubEventManageDTO(
                e.getId(),
                club.getId(), club.getName(), club.getSlug(), club.isTrusted(),
                e.getTitle(), e.getDescription(),
                e.getKind().name(), e.getKind().getDisplayName(),
                e.getStartsAt(), e.getEndsAt(),
                e.getLocation(), e.isOnline(), e.getLink(),
                e.isRecurring(), e.getRecurrenceNote(),
                target == null ? null : target.getCode(),
                target == null ? null : target.getName(),
                UnitTargetRule.joinForStorage(rule.prefixes()),
                e.getFaculty() == null ? null : e.getFaculty().name(),
                e.getLevel() == null ? null : e.getLevel().name(),
                rule.scopeLabel(),
                e.isShowOnHome(),
                e.getStatus().name(), e.getStatus().getDisplayName(),
                e.getRejectionReason(),
                creator == null ? null : creator.getEmail(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getPublishedAt(),
                e.getViewCount());
    }
}

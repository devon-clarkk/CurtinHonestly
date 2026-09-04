package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.ResourceKind;
import com.curtinhonestly.backend.domain.ResourceStatus;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.UnitResourceLink;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.dto.AdminUnitResourceLinkDTO;
import com.curtinhonestly.backend.dto.AdminUnitResourceOptionsDTO;
import com.curtinhonestly.backend.dto.AdminUnitResourcePreviewDTO;
import com.curtinhonestly.backend.dto.AdminUnitResourceReorderRequest;
import com.curtinhonestly.backend.dto.AdminUnitResourceUpsertRequest;
import com.curtinhonestly.backend.dto.UnitResourceLinkDTO;
import com.curtinhonestly.backend.dto.UnitResourceLinkListDTO;
import com.curtinhonestly.backend.dto.UnitResourceLinkSuggestionRequest;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UnitResourceLinkRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.util.SafeUrl;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Curated links on unit pages, plus the student suggestion queue and the admin
 * management operations behind them.
 *
 * Matching: a row with a target unit belongs to that unit only. A row without
 * one is a rule, and every criterion it sets must hold: the unit code starts
 * with one of its prefixes, the faculty equals, the level equals. A rule with
 * no criteria is site-wide. Results are de-duplicated by URL (the most
 * specific row wins) and ordered by kind, then sort order, then title.
 *
 * The approved set is small (tens of rows) and read on every unit page, so it
 * is loaded once into an immutable snapshot that lives for five minutes or
 * until any admin write, whichever comes first.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(rollbackOn = Exception.class)
public class UnitResourceLinkService {

    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(5);
    private static final int PREVIEW_SAMPLE_SIZE = 10;
    private static final int MAX_PREFIX_COUNT = 20;
    private static final Pattern PREFIX_SHAPE = Pattern.compile("^[A-Z0-9]{2,10}$");

    private final UnitResourceLinkRepo repo;
    private final UnitRepo unitRepo;
    private final UserRepo userRepo;
    private final ProfanityFilterService profanityFilterService;

    /**
     * An immutable, detached view of one approved row: everything matching and
     * the public DTO need, and nothing that could trip a lazy load later.
     */
    public record Rule(
            String id,
            String title,
            String url,
            String description,
            ResourceKind kind,
            String targetUnitId,
            List<String> prefixes,
            Faculty faculty,
            UnitLevel level,
            int sortOrder,
            ResourceStatus status
    ) {
        public static Rule from(UnitResourceLink r) {
            return new Rule(
                    r.getId(),
                    r.getTitle(),
                    r.getUrl(),
                    r.getDescription(),
                    r.getKind(),
                    r.getTargetUnit() == null ? null : r.getTargetUnit().getId(),
                    r.prefixList(),
                    r.getFaculty(),
                    r.getLevel(),
                    r.getSortOrder(),
                    r.getStatus());
        }

        /** Higher wins a URL tie: a unit-specific row over a three-criterion rule over a site-wide one. */
        int specificity() {
            if (targetUnitId != null) {
                return 4;
            }
            return (prefixes.isEmpty() ? 0 : 1) + (faculty == null ? 0 : 1) + (level == null ? 0 : 1);
        }
    }

    private record Snapshot(List<Rule> rules, Instant loadedAt) {}

    private volatile Snapshot snapshot;

    // ---------------------------------------------------------------- public

    /** Empty when the unit code is unknown (the controller turns that into a 404). */
    public Optional<UnitResourceLinkListDTO> resourcesForUnitCode(String code) {
        return unitRepo.findByCode(code)
                .map(unit -> new UnitResourceLinkListDTO(select(approvedRules(), unit)));
    }

    /** Counts a click on an approved resource. False when the id is unknown or not approved. */
    public boolean recordClick(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return repo.incrementClicks(id) > 0;
    }

    /** A signed-in student proposes a link for one unit. Lands as PENDING for an admin to review. */
    public UnitResourceLinkDTO suggest(String unitCode, UnitResourceLinkSuggestionRequest request) {
        Unit unit = unitRepo.findByCode(unitCode)
                .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + unitCode));
        User user = currentUser()
                .orElseThrow(() -> new IllegalStateException("You need to be signed in to suggest a link."));

        String title = requireText(request.title(), "A title", UnitResourceLink.MAX_TITLE);
        String url = SafeUrl.normalise(request.url());
        String description = optionalText(request.description(), "The description", UnitResourceLink.MAX_DESCRIPTION);
        String note = optionalText(request.note(), "The note", UnitResourceLink.MAX_NOTE);
        ResourceKind kind = ResourceKind.parse(request.kind());

        if (profanityFilterService.containsProfanity(title)
                || profanityFilterService.containsProfanity(description)
                || profanityFilterService.containsProfanity(note)) {
            throw new IllegalArgumentException("Your suggestion contains language that violates our community standards.");
        }

        boolean alreadyListed = select(approvedRules(), unit).stream()
                .anyMatch(existing -> existing.url().equalsIgnoreCase(url));
        if (alreadyListed) {
            throw new IllegalArgumentException("That link is already listed on this unit.");
        }
        boolean alreadySuggested = repo.findByStatusOrderByCreatedAtDesc(ResourceStatus.PENDING).stream()
                .anyMatch(p -> p.getUrl().equalsIgnoreCase(url)
                        && p.getTargetUnit() != null
                        && p.getTargetUnit().getId().equals(unit.getId()));
        if (alreadySuggested) {
            throw new IllegalArgumentException("That link has already been suggested for this unit and is waiting for review.");
        }

        UnitResourceLink link = new UnitResourceLink();
        link.setTitle(title);
        link.setUrl(url);
        link.setDescription(description);
        link.setKind(kind);
        link.setTargetUnit(unit);
        link.setStatus(ResourceStatus.PENDING);
        link.setSubmittedBy(user);
        link.setSubmitterNote(note);

        UnitResourceLink saved = repo.save(link);
        log.info("User {} suggested resource {} for unit {}", user.getId(), saved.getId(), unitCode);
        return toPublicDTO(Rule.from(saved));
    }

    // ----------------------------------------------------------------- admin

    public List<AdminUnitResourceLinkDTO> listAll(ResourceStatus status) {
        List<UnitResourceLink> rows = status == null
                ? repo.findAllByOrderByCreatedAtDesc()
                : repo.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(this::toAdminDTO).toList();
    }

    public AdminUnitResourceLinkDTO create(AdminUnitResourceUpsertRequest request) {
        UnitResourceLink link = new UnitResourceLink();
        applyUpsert(link, request);
        ResourceStatus status = request.status() == null || request.status().isBlank()
                ? ResourceStatus.APPROVED
                : ResourceStatus.parse(request.status());
        link.setStatus(status);
        if (status == ResourceStatus.APPROVED) {
            link.setApprovedAt(Instant.now());
        }
        currentUser().ifPresent(link::setSubmittedBy);
        UnitResourceLink saved = repo.save(link);
        invalidate();
        log.info("Admin created resource {} ({})", saved.getId(), saved.getTitle());
        return toAdminDTO(saved);
    }

    public AdminUnitResourceLinkDTO update(String id, AdminUnitResourceUpsertRequest request) {
        UnitResourceLink link = find(id);
        applyUpsert(link, request);
        UnitResourceLink saved = repo.save(link);
        invalidate();
        return toAdminDTO(saved);
    }

    public void delete(String id) {
        repo.deleteById(id);
        invalidate();
    }

    public AdminUnitResourceLinkDTO approve(String id) {
        UnitResourceLink link = find(id);
        link.setStatus(ResourceStatus.APPROVED);
        link.setApprovedAt(Instant.now());
        UnitResourceLink saved = repo.save(link);
        invalidate();
        log.info("Admin approved resource {} ({})", id, saved.getTitle());
        return toAdminDTO(saved);
    }

    public AdminUnitResourceLinkDTO reject(String id) {
        UnitResourceLink link = find(id);
        link.setStatus(ResourceStatus.REJECTED);
        link.setApprovedAt(null);
        UnitResourceLink saved = repo.save(link);
        invalidate();
        return toAdminDTO(saved);
    }

    public void reorder(AdminUnitResourceReorderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            return;
        }
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (AdminUnitResourceReorderRequest.Item item : request.items()) {
            if (item != null && item.id() != null) {
                wanted.put(item.id(), item.sortOrder());
            }
        }
        List<UnitResourceLink> rows = repo.findAllById(wanted.keySet());
        for (UnitResourceLink row : rows) {
            row.setSortOrder(wanted.get(row.getId()));
        }
        repo.saveAll(rows);
        invalidate();
    }

    /** How many units a rule (or a single unit code) would match, with a sample of codes. */
    public AdminUnitResourcePreviewDTO preview(String codePrefixes, String faculty, String level, String unitCode) {
        if (unitCode != null && !unitCode.isBlank()) {
            Optional<Unit> unit = unitRepo.findByCode(unitCode.trim().toUpperCase(Locale.ROOT));
            return unit
                    .map(u -> new AdminUnitResourcePreviewDTO(1, List.of(u.getCode()), scopeLabel(true, List.of(), null, null)))
                    .orElseGet(() -> new AdminUnitResourcePreviewDTO(0, List.of(), "Unit not found"));
        }
        List<String> prefixes = normalisePrefixes(codePrefixes);
        Faculty f = parseFaculty(faculty);
        UnitLevel l = parseLevel(level);

        int count = 0;
        List<String> sample = new ArrayList<>(PREVIEW_SAMPLE_SIZE);
        for (UnitResourceLinkRepo.UnitKey key : repo.unitKeysForPreview()) {
            if (matchesCriteria(prefixes, f, l, key.getCode(), key.getFaculty(), key.getLevel())) {
                count++;
                if (sample.size() < PREVIEW_SAMPLE_SIZE) {
                    sample.add(key.getCode());
                }
            }
        }
        return new AdminUnitResourcePreviewDTO(count, sample, scopeLabel(false, prefixes, f, l));
    }

    public AdminUnitResourceOptionsDTO options() {
        return new AdminUnitResourceOptionsDTO(
                Arrays.stream(ResourceKind.values())
                        .map(k -> new AdminUnitResourceOptionsDTO.Option(k.name(), k.getDisplayName())).toList(),
                Arrays.stream(Faculty.values())
                        .map(f -> new AdminUnitResourceOptionsDTO.Option(f.name(), f.getDisplayName())).toList(),
                Arrays.stream(UnitLevel.values())
                        .map(l -> new AdminUnitResourceOptionsDTO.Option(l.name(), l.getDisplayName())).toList());
    }

    // ------------------------------------------------------- pure matching

    /** Approved rules that apply to the unit, de-duplicated by URL and ordered for display. */
    public static List<UnitResourceLinkDTO> select(List<Rule> rules, Unit unit) {
        List<Rule> matching = rules.stream()
                .filter(r -> r.status() == ResourceStatus.APPROVED)
                .filter(r -> matches(r, unit.getId(), unit.getCode(), unit.getFaculty(), unit.getLevel()))
                .sorted(Comparator.comparingInt(Rule::specificity).reversed()
                        .thenComparingInt(Rule::sortOrder)
                        .thenComparing(r -> r.title().toLowerCase(Locale.ROOT)))
                .toList();

        Map<String, Rule> byUrl = new LinkedHashMap<>();
        for (Rule r : matching) {
            byUrl.putIfAbsent(r.url().toLowerCase(Locale.ROOT), r);
        }

        return byUrl.values().stream()
                .sorted(Comparator.comparingInt((Rule r) -> r.kind().ordinal())
                        .thenComparingInt(Rule::sortOrder)
                        .thenComparing(r -> r.title().toLowerCase(Locale.ROOT)))
                .map(UnitResourceLinkService::toPublicDTO)
                .toList();
    }

    public static boolean matches(Rule rule, String unitId, String code, Faculty faculty, UnitLevel level) {
        if (rule.targetUnitId() != null) {
            return rule.targetUnitId().equals(unitId);
        }
        return matchesCriteria(rule.prefixes(), rule.faculty(), rule.level(), code, faculty, level);
    }

    /** Every non-empty criterion must hold. No criteria at all matches everything. */
    public static boolean matchesCriteria(List<String> prefixes, Faculty ruleFaculty, UnitLevel ruleLevel,
                                          String code, Faculty unitFaculty, UnitLevel unitLevel) {
        if (!prefixes.isEmpty()) {
            String upper = code == null ? "" : code.toUpperCase(Locale.ROOT);
            boolean any = false;
            for (String p : prefixes) {
                if (upper.startsWith(p)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        if (ruleFaculty != null && ruleFaculty != unitFaculty) {
            return false;
        }
        return ruleLevel == null || ruleLevel == unitLevel;
    }

    /** Human-readable reason the link is on a page, shown as a chip next to it. */
    public static String scopeLabel(boolean unitSpecific, List<String> prefixes, Faculty faculty, UnitLevel level) {
        if (unitSpecific) {
            return "This unit";
        }
        boolean hasPrefixes = prefixes != null && !prefixes.isEmpty();
        if (!hasPrefixes && faculty == null && level == null) {
            return "All units";
        }
        if (hasPrefixes) {
            StringBuilder sb = new StringBuilder("All ");
            if (level != null) {
                sb.append(level.getDisplayName().toLowerCase(Locale.ROOT)).append(' ');
            }
            sb.append(joinPrefixes(prefixes)).append(" units");
            if (faculty != null) {
                sb.append(" in ").append(faculty.getDisplayName());
            }
            return sb.toString();
        }
        if (faculty != null && level != null) {
            return level.getDisplayName() + " " + faculty.getDisplayName();
        }
        if (faculty != null) {
            return faculty.getDisplayName();
        }
        return level.getDisplayName() + " units";
    }

    private static String joinPrefixes(List<String> prefixes) {
        int n = prefixes.size();
        if (n == 1) {
            return prefixes.get(0);
        }
        if (n == 2) {
            return prefixes.get(0) + " and " + prefixes.get(1);
        }
        if (n <= 4) {
            return String.join(", ", prefixes.subList(0, n - 1)) + " and " + prefixes.get(n - 1);
        }
        return String.join(", ", prefixes.subList(0, 3)) + " and " + (n - 3) + " more";
    }

    public static UnitResourceLinkDTO toPublicDTO(Rule r) {
        return new UnitResourceLinkDTO(
                r.id(),
                r.title(),
                r.url(),
                r.description(),
                r.kind().name(),
                r.kind().getDisplayName(),
                scopeLabel(r.targetUnitId() != null, r.prefixes(), r.faculty(), r.level()));
    }

    // -------------------------------------------------------------- helpers

    private List<Rule> approvedRules() {
        Snapshot current = snapshot;
        Instant now = Instant.now();
        if (current == null || current.loadedAt().plus(SNAPSHOT_TTL).isBefore(now)) {
            List<Rule> rules = repo.findByStatusOrderBySortOrderAscTitleAsc(ResourceStatus.APPROVED).stream()
                    .map(Rule::from)
                    .toList();
            current = new Snapshot(rules, now);
            snapshot = current;
        }
        return current.rules();
    }

    /** Drops the cached approved set; the next public read reloads it. */
    public void invalidate() {
        snapshot = null;
    }

    private UnitResourceLink find(String id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found."));
    }

    private void applyUpsert(UnitResourceLink link, AdminUnitResourceUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A request body is required.");
        }
        link.setTitle(requireText(request.title(), "A title", UnitResourceLink.MAX_TITLE));
        link.setUrl(SafeUrl.normalise(request.url()));
        link.setDescription(optionalText(request.description(), "The description", UnitResourceLink.MAX_DESCRIPTION));
        link.setKind(ResourceKind.parse(request.kind()));
        if (request.sortOrder() != null) {
            link.setSortOrder(request.sortOrder());
        }

        if (request.unitCode() != null && !request.unitCode().isBlank()) {
            String code = request.unitCode().trim().toUpperCase(Locale.ROOT);
            Unit unit = unitRepo.findByCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + code));
            link.setTargetUnit(unit);
            link.setCodePrefixes(null);
            link.setFaculty(null);
            link.setLevel(null);
            return;
        }

        List<String> prefixes = normalisePrefixes(request.codePrefixes());
        link.setTargetUnit(null);
        link.setCodePrefixes(prefixes.isEmpty() ? null : String.join(",", prefixes));
        link.setFaculty(parseFaculty(request.faculty()));
        link.setLevel(parseLevel(request.level()));
    }

    /** Splits, upper-cases and validates a prefix list. Empty input means "no prefix criterion". */
    static List<String> normalisePrefixes(String raw) {
        List<String> prefixes = UnitResourceLink.splitPrefixes(raw);
        if (prefixes.size() > MAX_PREFIX_COUNT) {
            throw new IllegalArgumentException("Use at most " + MAX_PREFIX_COUNT + " code prefixes.");
        }
        for (String p : prefixes) {
            if (!PREFIX_SHAPE.matcher(p).matches()) {
                throw new IllegalArgumentException("Code prefixes must be 2 to 10 letters or digits, e.g. COMP or ISAD. Got: " + p);
            }
        }
        String joined = String.join(",", prefixes);
        if (joined.length() > UnitResourceLink.MAX_PREFIXES) {
            throw new IllegalArgumentException("The prefix list is too long.");
        }
        return prefixes;
    }

    /** Accepts the enum name or the display name, case-insensitively. Blank is null. */
    static Faculty parseFaculty(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String wanted = raw.trim();
        for (Faculty f : Faculty.values()) {
            if (f.name().equalsIgnoreCase(wanted) || f.getDisplayName().equalsIgnoreCase(wanted)) {
                return f;
            }
        }
        throw new IllegalArgumentException("Unknown faculty: " + wanted);
    }

    static UnitLevel parseLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String wanted = raw.trim();
        for (UnitLevel l : UnitLevel.values()) {
            if (l.name().equalsIgnoreCase(wanted) || l.getDisplayName().equalsIgnoreCase(wanted)) {
                return l;
            }
        }
        throw new IllegalArgumentException("Unknown level: " + wanted);
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        return userRepo.findByEmail(name);
    }

    private AdminUnitResourceLinkDTO toAdminDTO(UnitResourceLink r) {
        Unit target = r.getTargetUnit();
        List<String> prefixes = r.prefixList();
        User submitter = r.getSubmittedBy();
        String submittedBy = null;
        if (submitter != null) {
            submittedBy = submitter.getRoles() != null && submitter.getRoles().contains(UserRole.ROLE_ADMIN)
                    ? "admin"
                    : "student";
        }
        return new AdminUnitResourceLinkDTO(
                r.getId(),
                r.getTitle(),
                r.getUrl(),
                r.getDescription(),
                r.getKind().name(),
                r.getKind().getDisplayName(),
                target == null ? null : target.getCode(),
                target == null ? null : target.getName(),
                prefixes.isEmpty() ? null : String.join(",", prefixes),
                r.getFaculty() == null ? null : r.getFaculty().name(),
                r.getFaculty() == null ? null : r.getFaculty().getDisplayName(),
                r.getLevel() == null ? null : r.getLevel().name(),
                r.getLevel() == null ? null : r.getLevel().getDisplayName(),
                scopeLabel(target != null, prefixes, r.getFaculty(), r.getLevel()),
                r.getStatus().name(),
                r.getSortOrder(),
                r.getClickCount(),
                submittedBy,
                r.getSubmitterNote(),
                r.getCreatedAt(),
                r.getApprovedAt());
    }
}

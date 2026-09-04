package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.AcademicTerm;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.*;
import com.curtinhonestly.backend.repo.ReviewFlagRepo;
import com.curtinhonestly.backend.repo.ReviewLikeRepo;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UnitRequestRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackOn = Exception.class)
public class AdminService {

    private static final int LEADERBOARD_SIZE = 10;
    private static final int RECENT_TERMS = 8;
    private static final int OVERVIEW_SERIES_DAYS = 30;
    private static final int EXCERPT_LENGTH = 140;

    private final UserRepo userRepo;
    private final ReviewRepo reviewRepo;
    private final UnitRepo unitRepo;
    private final ReviewFlagRepo reviewFlagRepo;
    private final ReviewLikeRepo reviewLikeRepo;
    private final UnitRequestRepo unitRequestRepo;
    private final UserService userService;

    // Every figure here is a count or group-by query. Nothing loads the reviews
    // table into memory: the dashboard must stay cheap as the site grows.
    public AdminOverviewDTO getOverview() {
        Instant now = Instant.now();
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        Instant fourteenDaysAgo = now.minus(14, ChronoUnit.DAYS);

        long totalUsers = userRepo.count();
        long verifiedUsers = userRepo.countByVerifiedStudentTrue();
        long totalReviews = reviewRepo.count();
        long totalUnits = unitRepo.count();
        long unitsWithReviews = reviewRepo.countDistinctReviewedUnits();

        return new AdminOverviewDTO(
                totalUsers,
                verifiedUsers,
                userRepo.countByBannedTrue(),
                totalReviews,
                reviewRepo.countWithText(),
                unitsWithReviews,
                totalUnits,
                ratio(unitsWithReviews, totalUnits),
                unitRequestRepo.count(),
                reviewFlagRepo.countDistinctFlaggedReviews(),
                reviewLikeRepo.count(),
                userRepo.countByCreatedAtAfter(sevenDaysAgo),
                userRepo.countByCreatedAtBetween(fourteenDaysAgo, sevenDaysAgo),
                reviewRepo.countByCreatedAtAfter(sevenDaysAgo),
                reviewRepo.countByCreatedAtBetween(fourteenDaysAgo, sevenDaysAgo),
                userRepo.countByVerifiedStudentFalseAndCreatedAtAfter(sevenDaysAgo),
                ratio(verifiedUsers, totalUsers),
                buildTimeSeries(OVERVIEW_SERIES_DAYS),
                topUnits(),
                mostRequestedUnits()
        );
    }

    public AdminAnalyticsDTO getAnalytics(int days) {
        int periodDays = Math.max(1, days);
        long totalUsers = userRepo.count();
        long totalReviews = reviewRepo.count();
        long reviewsWithAuthor = reviewRepo.countByUserIsNotNull();
        long activeReviewers = reviewRepo.countDistinctAuthors();

        return new AdminAnalyticsDTO(
                periodDays,
                buildTimeSeries(periodDays),
                totalUsers,
                totalReviews,
                activeReviewers,
                ratio(userRepo.countByVerifiedStudentTrue(), totalUsers),
                ratio(activeReviewers, totalUsers),
                activeReviewers == 0 ? 0 : round1((double) reviewsWithAuthor / activeReviewers),
                ratingDistribution(),
                workloadDistribution(),
                round1(orZero(reviewRepo.averageWorkload())),
                ratio(reviewRepo.countByWouldTakeAgainTrue(), totalReviews),
                Math.round(orZero(reviewRepo.averageReviewTextLength())),
                ratio(reviewRepo.countByFinalGradeIsNotNull(), totalReviews),
                facultyBreakdown(),
                reviewsByTerm(),
                mostLikedReviews(),
                mostActiveReviewers()
        );
    }

    public List<UserAdminDTO> listUsers() {
        return userRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toUserAdminDTO)
                .toList();
    }

    public Page<AdminReviewDTO> listReviews(int page, int size) {
        Page<Review> reviews = reviewRepo.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<String, Long> flagCounts = flagCountsFor(reviews.getContent());
        return reviews.map(review -> toAdminReviewDTO(review, flagCounts.getOrDefault(review.getId(), 0L)));
    }

    public Optional<AdminReviewDTO> getReview(String reviewId) {
        return reviewRepo.findById(reviewId)
                .map(review -> toAdminReviewDTO(review, reviewFlagRepo.countByReview_Id(reviewId)));
    }

    public UserAdminDTO createUser(String email, String password, boolean admin) {
        User user = admin
                ? userService.createAdminUser(email, password)
                : userService.createUser(email, password);
        return toUserAdminDTO(user);
    }

    public UserAdminDTO setBanned(String userId, boolean banned) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setBanned(banned);
        return toUserAdminDTO(userRepo.save(user));
    }

    // Manual override for the email verification flow: when the verification
    // email does not arrive, an admin can mark the student verified by hand.
    public UserAdminDTO setVerifiedStudent(String userId, boolean verified) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setVerifiedStudent(verified);
        User saved = userRepo.save(user);
        log.info("Admin {} {} user {}", currentAdminName(), verified ? "verified" : "unverified", saved.getId());
        return toUserAdminDTO(saved);
    }

    public void deleteUser(String userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        userRepo.delete(user);
    }

    // Leaderboards and distributions

    private List<AdminUnitLeaderDTO> topUnits() {
        return reviewRepo.findTopUnitsByReviewCount(PageRequest.of(0, LEADERBOARD_SIZE)).stream()
                .map(row -> new AdminUnitLeaderDTO(
                        asString(row[0]),
                        asString(row[1]),
                        asLong(row[2]),
                        round1(asDouble(row[3]))))
                .toList();
    }

    // Requested codes are folded case-insensitively (isys1000 and ISYS1000 are
    // the same ask) and trimmed, then ranked by how many students asked.
    private List<AdminRequestedUnitDTO> mostRequestedUnits() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : unitRequestRepo.countGroupedByRequestedCode()) {
            String code = asString(row[0]).trim().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) continue;
            counts.merge(code, asLong(row[1]), Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(LEADERBOARD_SIZE)
                .map(e -> new AdminRequestedUnitDTO(e.getKey(), e.getValue()))
                .toList();
    }

    private List<AdminDistributionBucketDTO> ratingDistribution() {
        Map<Integer, Long> byRating = groupCounts(reviewRepo.countGroupedByRating());
        List<AdminDistributionBucketDTO> buckets = new ArrayList<>();
        for (int stars = 5; stars >= 1; stars--) {
            buckets.add(new AdminDistributionBucketDTO(stars + " star" + (stars == 1 ? "" : "s"),
                    byRating.getOrDefault(stars, 0L)));
        }
        return buckets;
    }

    // Workload is stored 0-10; five bands keep the histogram readable.
    private List<AdminDistributionBucketDTO> workloadDistribution() {
        Map<Integer, Long> byWorkload = groupCounts(reviewRepo.countGroupedByWorkload());
        int[][] bands = {{0, 2}, {3, 4}, {5, 6}, {7, 8}, {9, 10}};
        List<AdminDistributionBucketDTO> buckets = new ArrayList<>();
        for (int[] band : bands) {
            long count = 0;
            for (int value = band[0]; value <= band[1]; value++) {
                count += byWorkload.getOrDefault(value, 0L);
            }
            buckets.add(new AdminDistributionBucketDTO(band[0] + "-" + band[1], count));
        }
        return buckets;
    }

    private List<AdminFacultyBreakdownDTO> facultyBreakdown() {
        Map<Faculty, Long> unitsByFaculty = new EnumMap<>(Faculty.class);
        for (Object[] row : unitRepo.countGroupedByFaculty()) {
            if (row[0] instanceof Faculty faculty) unitsByFaculty.put(faculty, asLong(row[1]));
        }
        Map<Faculty, long[]> reviewsByFaculty = new EnumMap<>(Faculty.class);
        for (Object[] row : reviewRepo.countReviewsAndUnitsGroupedByFaculty()) {
            if (row[0] instanceof Faculty faculty) {
                reviewsByFaculty.put(faculty, new long[]{asLong(row[1]), asLong(row[2])});
            }
        }
        List<AdminFacultyBreakdownDTO> result = new ArrayList<>();
        for (Faculty faculty : Faculty.values()) {
            long[] reviewed = reviewsByFaculty.getOrDefault(faculty, new long[]{0, 0});
            result.add(new AdminFacultyBreakdownDTO(
                    faculty.name(),
                    faculty.getDisplayName(),
                    unitsByFaculty.getOrDefault(faculty, 0L),
                    reviewed[1],
                    reviewed[0]));
        }
        result.sort(Comparator.comparingLong(AdminFacultyBreakdownDTO::reviews).reversed()
                .thenComparing(AdminFacultyBreakdownDTO::label));
        return result;
    }

    // Most recent terms first. Within a year the chronological order is Summer
    // (ends in February), Semester 1, Semester 2, so the reverse puts Semester 2
    // first. EARLIER_UNSPECIFIED has no year and is left out of the series.
    private List<AdminTermCountDTO> reviewsByTerm() {
        List<AdminTermCountDTO> terms = new ArrayList<>();
        for (Object[] row : reviewRepo.countGroupedByTerm()) {
            if (!(row[0] instanceof AcademicTerm term) || row[1] == null) continue;
            if (term == AcademicTerm.EARLIER_UNSPECIFIED) continue;
            terms.add(new AdminTermCountDTO(term.name(), asInt(row[1]), asLong(row[2])));
        }
        terms.sort(Comparator.comparing(AdminTermCountDTO::termYear, Comparator.reverseOrder())
                .thenComparing(t -> termOrder(t.termType()), Comparator.reverseOrder()));
        return terms.stream().limit(RECENT_TERMS).toList();
    }

    private static int termOrder(String termType) {
        return switch (termType) {
            case "SUMMER" -> 0;
            case "SEMESTER_1" -> 1;
            case "SEMESTER_2" -> 2;
            default -> -1;
        };
    }

    private List<AdminLikedReviewDTO> mostLikedReviews() {
        return reviewRepo.findTop10ByOrderByLikeCountDescCreatedAtDesc().stream()
                .filter(review -> review.getLikeCount() > 0)
                .map(review -> new AdminLikedReviewDTO(
                        review.getId(),
                        review.getUnit() != null ? review.getUnit().getCode() : "unknown",
                        review.getLikeCount(),
                        excerpt(review.getReviewText())))
                .toList();
    }

    private List<AdminReviewerDTO> mostActiveReviewers() {
        return reviewRepo.findTopReviewers(PageRequest.of(0, LEADERBOARD_SIZE)).stream()
                .map(row -> new AdminReviewerDTO(maskEmail(asString(row[0])), asLong(row[1]), asLong(row[2])))
                .toList();
    }

    // Mapping

    private UserAdminDTO toUserAdminDTO(User user) {
        long reviewCount = user.getReviews() != null ? user.getReviews().size() : 0;
        List<String> roles = user.getRoles().stream().map(Enum::name).toList();
        return new UserAdminDTO(
                user.getId(),
                user.getEmail(),
                user.isVerifiedStudent(),
                roles,
                user.isBanned(),
                reviewCount,
                user.getCreatedAt()
        );
    }

    private AdminReviewDTO toAdminReviewDTO(Review review, long flagCount) {
        String unitCode = review.getUnit() != null ? review.getUnit().getCode() : "unknown";
        String unitName = review.getUnit() != null ? review.getUnit().getName() : "";
        User author = review.getUser();
        List<String> tags = review.getTags() == null ? List.of()
                : review.getTags().stream().map(Enum::name).sorted().toList();
        return new AdminReviewDTO(
                review.getId(),
                unitCode,
                unitName,
                author != null ? author.getEmail() : "anonymous",
                author != null ? author.getId() : null,
                author != null && author.isVerifiedStudent(),
                review.getRating(),
                review.getFinalGrade(),
                review.getReviewText(),
                review.getTermType() != null ? review.getTermType().name() : null,
                review.getTermYear(),
                review.getProfessor(),
                review.getWorkload(),
                review.isHasExam(),
                review.isWouldTakeAgain(),
                tags,
                review.getLikeCount(),
                flagCount,
                review.getCreatedAt()
        );
    }

    private Map<String, Long> flagCountsFor(List<Review> reviews) {
        if (reviews.isEmpty()) return Map.of();
        List<String> ids = reviews.stream().map(Review::getId).toList();
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : reviewFlagRepo.countGroupedByReviewIds(ids)) {
            counts.put(asString(row[0]), asLong(row[1]));
        }
        return counts;
    }

    // Daily buckets for the last N days (UTC), oldest first. Only the createdAt
    // instants are fetched, never the entities.
    private List<TimeSeriesPointDTO> buildTimeSeries(int days) {
        LocalDate start = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L);
        Instant since = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        return buildTimeSeries(userRepo.findCreatedAtSince(since), reviewRepo.findCreatedAtSince(since), days);
    }

    List<TimeSeriesPointDTO> buildTimeSeries(List<Instant> userCreatedAt, List<Instant> reviewCreatedAt, int days) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        Map<LocalDate, long[]> buckets = new TreeMap<>();

        LocalDate start = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            buckets.put(start.plusDays(i), new long[]{0, 0});
        }

        for (Instant createdAt : userCreatedAt) {
            if (createdAt == null) continue;
            long[] bucket = buckets.get(createdAt.atZone(ZoneOffset.UTC).toLocalDate());
            if (bucket != null) bucket[0]++;
        }

        for (Instant createdAt : reviewCreatedAt) {
            if (createdAt == null) continue;
            long[] bucket = buckets.get(createdAt.atZone(ZoneOffset.UTC).toLocalDate());
            if (bucket != null) bucket[1]++;
        }

        return buckets.entrySet().stream()
                .map(e -> new TimeSeriesPointDTO(
                        e.getKey().format(formatter),
                        e.getValue()[0],
                        e.getValue()[1]
                ))
                .toList();
    }

    // Helpers

    // d***@student.curtin.edu.au: first character of the local part, the rest hidden.
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "unknown";
        int at = email.indexOf('@');
        String local = at >= 0 ? email.substring(0, at) : email;
        String domain = at >= 0 ? email.substring(at) : "";
        String head = local.isEmpty() ? "" : local.substring(0, 1);
        return head + "***" + domain;
    }

    static String excerpt(String text) {
        if (text == null) return "";
        String collapsed = text.replaceAll("\\s+", " ").trim();
        if (collapsed.length() <= EXCERPT_LENGTH) return collapsed;
        return collapsed.substring(0, EXCERPT_LENGTH - 1).trim() + "…";
    }

    static double ratio(long part, long whole) {
        if (whole <= 0) return 0;
        return Math.round(((double) part / whole) * 1000.0) / 1000.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double orZero(Double value) {
        return value == null ? 0 : value;
    }

    private static Map<Integer, Long> groupCounts(List<Object[]> rows) {
        return rows.stream()
                .filter(row -> row[0] != null)
                .collect(Collectors.toMap(row -> asInt(row[0]), row -> asLong(row[1]), Long::sum));
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0d;
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String currentAdminName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "unknown";
    }
}

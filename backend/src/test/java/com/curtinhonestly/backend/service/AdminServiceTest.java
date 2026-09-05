package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.AcademicTerm;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.ReviewTag;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.AdminAnalyticsDTO;
import com.curtinhonestly.backend.dto.AdminDistributionBucketDTO;
import com.curtinhonestly.backend.dto.AdminOverviewDTO;
import com.curtinhonestly.backend.dto.AdminReviewDTO;
import com.curtinhonestly.backend.dto.TimeSeriesPointDTO;
import com.curtinhonestly.backend.dto.UserAdminDTO;
import com.curtinhonestly.backend.repo.ReviewFlagRepo;
import com.curtinhonestly.backend.repo.ReviewLikeRepo;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UnitRequestRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

// Pure unit tests for the admin dashboard service: the aggregation, masking and
// mapping logic on top of mocked repositories. The JPQL itself is not exercised
// here (no database); these cover what the service does with the rows.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminServiceTest {

    @Mock UserRepo userRepo;
    @Mock ReviewRepo reviewRepo;
    @Mock UnitRepo unitRepo;
    @Mock ReviewFlagRepo reviewFlagRepo;
    @Mock ReviewLikeRepo reviewLikeRepo;
    @Mock UnitRequestRepo unitRequestRepo;
    @Mock UserService userService;

    private AdminService service() {
        return new AdminService(userRepo, reviewRepo, unitRepo, reviewFlagRepo, reviewLikeRepo, unitRequestRepo, userService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // Helpers

    @Test
    void maskEmailKeepsFirstCharacterAndDomain() {
        assertThat(AdminService.maskEmail("devon@student.curtin.edu.au")).isEqualTo("d***@student.curtin.edu.au");
        assertThat(AdminService.maskEmail("x@y.z")).isEqualTo("x***@y.z");
        assertThat(AdminService.maskEmail("nodomain")).isEqualTo("n***");
        assertThat(AdminService.maskEmail(null)).isEqualTo("unknown");
        assertThat(AdminService.maskEmail("")).isEqualTo("unknown");
    }

    @Test
    void excerptCollapsesWhitespaceAndTruncates() {
        assertThat(AdminService.excerpt("  hello\n\n  world  ")).isEqualTo("hello world");
        assertThat(AdminService.excerpt(null)).isEmpty();
        String longText = "a".repeat(300);
        String excerpt = AdminService.excerpt(longText);
        assertThat(excerpt).hasSize(140);
        assertThat(excerpt).endsWith("…");
    }

    @Test
    void ratioIsZeroSafeAndRoundedToThreePlaces() {
        assertThat(AdminService.ratio(1, 0)).isZero();
        assertThat(AdminService.ratio(1, 3)).isEqualTo(0.333);
        assertThat(AdminService.ratio(2, 2)).isEqualTo(1.0);
    }

    @Test
    void buildTimeSeriesBucketsByUtcDayAndFillsEmptyDays() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant todayNoon = today.atTime(12, 0).toInstant(ZoneOffset.UTC);
        Instant yesterdayLate = today.minusDays(1).atTime(23, 59).toInstant(ZoneOffset.UTC);
        Instant tooOld = today.minusDays(10).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<TimeSeriesPointDTO> series = service().buildTimeSeries(
                List.of(todayNoon, tooOld),
                List.of(todayNoon, todayNoon, yesterdayLate),
                3);

        assertThat(series).hasSize(3);
        assertThat(series.get(0).period()).isEqualTo(today.minusDays(2).toString());
        assertThat(series.get(0).users()).isZero();
        assertThat(series.get(0).reviews()).isZero();
        assertThat(series.get(1).reviews()).isEqualTo(1);
        assertThat(series.get(2).period()).isEqualTo(today.toString());
        assertThat(series.get(2).users()).isEqualTo(1);
        assertThat(series.get(2).reviews()).isEqualTo(2);
    }

    // Verification override

    @Test
    void setVerifiedStudentPersistsTheFlagAndReturnsTheRefreshedUser() {
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken("admin@curtinhonestly.com", "pw", List.of())));
        User user = user("user-1", "s@student.curtin.edu.au", false);
        when(userRepo.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAdminDTO dto = service().setVerifiedStudent("user-1", true);

        assertThat(dto.verifiedStudent()).isTrue();
        assertThat(user.isVerifiedStudent()).isTrue();
        verify(userRepo).save(user);

        UserAdminDTO reverted = service().setVerifiedStudent("user-1", false);
        assertThat(reverted.verifiedStudent()).isFalse();
    }

    @Test
    void setVerifiedStudentRejectsUnknownUser() {
        when(userRepo.findById("missing")).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().setVerifiedStudent("missing", true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
        verify(userRepo, never()).save(any());
    }

    // Review detail

    @Test
    void getReviewReturnsEveryStoredFieldWithFlagCount() {
        Review review = review("review-1", user("user-1", "s@student.curtin.edu.au", true));
        when(reviewRepo.findById("review-1")).thenReturn(Optional.of(review));
        when(reviewFlagRepo.countByReview_Id("review-1")).thenReturn(2L);

        Optional<AdminReviewDTO> result = service().getReview("review-1");

        assertThat(result).isPresent();
        AdminReviewDTO dto = result.get();
        assertThat(dto.unitCode()).isEqualTo("ISYS1000");
        assertThat(dto.unitName()).isEqualTo("Intro to Systems");
        assertThat(dto.authorEmail()).isEqualTo("s@student.curtin.edu.au");
        assertThat(dto.authorId()).isEqualTo("user-1");
        assertThat(dto.authorVerified()).isTrue();
        assertThat(dto.rating()).isEqualTo(4);
        assertThat(dto.finalGrade()).isEqualTo(78);
        assertThat(dto.reviewText()).isEqualTo("Line one.\nLine two.");
        assertThat(dto.termType()).isEqualTo("SEMESTER_2");
        assertThat(dto.termYear()).isEqualTo(2025);
        assertThat(dto.professor()).isEqualTo("Dr Example");
        assertThat(dto.workload()).isEqualTo(7);
        assertThat(dto.hasExam()).isTrue();
        assertThat(dto.wouldTakeAgain()).isFalse();
        assertThat(dto.tags()).containsExactly("GROUP_WORK", "WEEKLY_QUIZZES");
        assertThat(dto.likeCount()).isEqualTo(3);
        assertThat(dto.flagCount()).isEqualTo(2);
    }

    @Test
    void getReviewIsEmptyWhenMissing() {
        when(reviewRepo.findById("nope")).thenReturn(Optional.empty());
        assertThat(service().getReview("nope")).isEmpty();
    }

    @Test
    void anonymisedReviewsReportAnonymousAuthor() {
        Review review = review("review-2", null);
        when(reviewRepo.findById("review-2")).thenReturn(Optional.of(review));
        when(reviewFlagRepo.countByReview_Id("review-2")).thenReturn(0L);

        AdminReviewDTO dto = service().getReview("review-2").orElseThrow();

        assertThat(dto.authorEmail()).isEqualTo("anonymous");
        assertThat(dto.authorId()).isNull();
        assertThat(dto.authorVerified()).isFalse();
    }

    @Test
    void listReviewsFetchesFlagCountsForThePageInOneQuery() {
        Review first = review("review-1", null);
        Review second = review("review-2", null);
        when(reviewRepo.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 2));
        when(reviewFlagRepo.countGroupedByReviewIds(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{"review-2", 5L}));

        List<AdminReviewDTO> page = service().listReviews(0, 20).getContent();

        assertThat(page).extracting(AdminReviewDTO::flagCount).containsExactly(0L, 5L);
        verify(reviewFlagRepo, times(1)).countGroupedByReviewIds(anyCollection());
        verify(reviewFlagRepo, never()).countByReview_Id(any());
    }

    // Aggregates

    @Test
    void overviewComputesRatesAndFoldsRequestedCodesCaseInsensitively() {
        when(userRepo.count()).thenReturn(200L);
        when(userRepo.countByVerifiedStudentTrue()).thenReturn(50L);
        when(userRepo.countByBannedTrue()).thenReturn(3L);
        when(reviewRepo.count()).thenReturn(80L);
        when(reviewRepo.countWithText()).thenReturn(60L);
        when(reviewRepo.countDistinctReviewedUnits()).thenReturn(25L);
        when(unitRepo.count()).thenReturn(1000L);
        when(unitRequestRepo.count()).thenReturn(7L);
        when(reviewFlagRepo.countDistinctFlaggedReviews()).thenReturn(2L);
        when(reviewLikeRepo.count()).thenReturn(40L);
        when(userRepo.countByCreatedAtAfter(any())).thenReturn(12L);
        when(userRepo.countByCreatedAtBetween(any(), any())).thenReturn(8L);
        when(reviewRepo.countByCreatedAtAfter(any())).thenReturn(5L);
        when(reviewRepo.countByCreatedAtBetween(any(), any())).thenReturn(9L);
        when(userRepo.countByVerifiedStudentFalseAndCreatedAtAfter(any())).thenReturn(4L);
        when(userRepo.findCreatedAtSince(any())).thenReturn(List.of());
        when(reviewRepo.findCreatedAtSince(any())).thenReturn(List.of());
        when(reviewRepo.findTopUnitsByReviewCount(any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{"ISYS1000", "Intro", 9L, 4.25}));
        when(unitRequestRepo.countGroupedByRequestedCode()).thenReturn(List.<Object[]>of(
                new Object[]{"comp1000", 2L},
                new Object[]{"COMP1000 ", 3L},
                new Object[]{"MATH1010", 4L}));

        AdminOverviewDTO overview = service().getOverview();

        assertThat(overview.verificationRate()).isEqualTo(0.25);
        assertThat(overview.coverageRatio()).isEqualTo(0.025);
        assertThat(overview.usersLast7Days()).isEqualTo(12);
        assertThat(overview.usersPrior7Days()).isEqualTo(8);
        assertThat(overview.reviewsLast7Days()).isEqualTo(5);
        assertThat(overview.reviewsPrior7Days()).isEqualTo(9);
        assertThat(overview.unverifiedUsersLast7Days()).isEqualTo(4);
        assertThat(overview.openFlaggedReviews()).isEqualTo(2);
        assertThat(overview.totalLikes()).isEqualTo(40);
        assertThat(overview.signupsAndReviewsOverTime()).hasSize(30);
        assertThat(overview.topUnits()).hasSize(1);
        assertThat(overview.topUnits().get(0).averageRating()).isEqualTo(4.3);
        assertThat(overview.mostRequestedUnits()).extracting("code", "count")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("COMP1000", 5L),
                        org.assertj.core.groups.Tuple.tuple("MATH1010", 4L));
        verify(reviewRepo, never()).findAll();
    }

    @Test
    void analyticsFillsDistributionsAndOrdersTermsMostRecentFirst() {
        when(userRepo.count()).thenReturn(100L);
        when(userRepo.countByVerifiedStudentTrue()).thenReturn(30L);
        when(reviewRepo.count()).thenReturn(40L);
        when(reviewRepo.countByUserIsNotNull()).thenReturn(36L);
        when(reviewRepo.countDistinctAuthors()).thenReturn(12L);
        when(userRepo.findCreatedAtSince(any())).thenReturn(List.of());
        when(reviewRepo.findCreatedAtSince(any())).thenReturn(List.of());
        when(reviewRepo.countGroupedByRating()).thenReturn(List.<Object[]>of(
                new Object[]{5, 10L}, new Object[]{3, 4L}));
        when(reviewRepo.countGroupedByWorkload()).thenReturn(List.<Object[]>of(
                new Object[]{0, 1L}, new Object[]{2, 2L}, new Object[]{10, 3L}));
        when(reviewRepo.averageWorkload()).thenReturn(6.26);
        when(reviewRepo.countByWouldTakeAgainTrue()).thenReturn(30L);
        when(reviewRepo.averageReviewTextLength()).thenReturn(210.6);
        when(reviewRepo.countByFinalGradeIsNotNull()).thenReturn(10L);
        when(unitRepo.countGroupedByFaculty()).thenReturn(List.<Object[]>of(
                new Object[]{Faculty.SCIENCE_AND_ENGINEERING, 400L},
                new Object[]{Faculty.HUMANITIES, 100L}));
        when(reviewRepo.countReviewsAndUnitsGroupedByFaculty()).thenReturn(List.<Object[]>of(
                new Object[]{Faculty.SCIENCE_AND_ENGINEERING, 35L, 20L}));
        when(reviewRepo.countGroupedByTerm()).thenReturn(List.<Object[]>of(
                new Object[]{AcademicTerm.SEMESTER_1, 2026, 5L},
                new Object[]{AcademicTerm.SUMMER, 2026, 1L},
                new Object[]{AcademicTerm.SEMESTER_2, 2025, 7L},
                new Object[]{AcademicTerm.EARLIER_UNSPECIFIED, null, 2L}));
        when(reviewRepo.findTop10ByOrderByLikeCountDescCreatedAtDesc()).thenReturn(List.of(review("r", null)));
        when(reviewRepo.findTopReviewers(any(Pageable.class))).thenReturn(List.<Object[]>of(
                new Object[]{"devon@student.curtin.edu.au", 9L, 14L}));

        AdminAnalyticsDTO analytics = service().getAnalytics(14);

        assertThat(analytics.periodDays()).isEqualTo(14);
        assertThat(analytics.signupsAndReviewsOverTime()).hasSize(14);
        assertThat(analytics.verificationRate()).isEqualTo(0.3);
        assertThat(analytics.activeReviewerShare()).isEqualTo(0.12);
        assertThat(analytics.reviewsPerActiveReviewer()).isEqualTo(3.0);
        assertThat(analytics.ratingDistribution()).extracting(AdminDistributionBucketDTO::label)
                .containsExactly("5 stars", "4 stars", "3 stars", "2 stars", "1 star");
        assertThat(analytics.ratingDistribution()).extracting(AdminDistributionBucketDTO::count)
                .containsExactly(10L, 0L, 4L, 0L, 0L);
        assertThat(analytics.workloadDistribution()).extracting(AdminDistributionBucketDTO::count)
                .containsExactly(3L, 0L, 0L, 0L, 3L);
        assertThat(analytics.averageWorkload()).isEqualTo(6.3);
        assertThat(analytics.wouldTakeAgainRatio()).isEqualTo(0.75);
        assertThat(analytics.averageReviewTextLength()).isEqualTo(211.0);
        assertThat(analytics.gradeShare()).isEqualTo(0.25);

        assertThat(analytics.facultyBreakdown()).hasSize(Faculty.values().length);
        assertThat(analytics.facultyBreakdown().get(0).faculty()).isEqualTo("SCIENCE_AND_ENGINEERING");
        assertThat(analytics.facultyBreakdown().get(0).units()).isEqualTo(400);
        assertThat(analytics.facultyBreakdown().get(0).unitsWithReviews()).isEqualTo(20);
        assertThat(analytics.facultyBreakdown().get(0).reviews()).isEqualTo(35);

        assertThat(analytics.reviewsByTerm()).extracting(t -> t.termType() + "/" + t.termYear())
                .containsExactly("SEMESTER_1/2026", "SUMMER/2026", "SEMESTER_2/2025");

        assertThat(analytics.mostLikedReviews()).hasSize(1);
        assertThat(analytics.mostActiveReviewers()).hasSize(1);
        assertThat(analytics.mostActiveReviewers().get(0).maskedEmail()).isEqualTo("d***@student.curtin.edu.au");
        assertThat(analytics.mostActiveReviewers().get(0).likesReceived()).isEqualTo(14);
        verify(reviewRepo, never()).findAll();
    }

    // Fixtures

    private static User user(String id, String email, boolean verified) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setVerifiedStudent(verified);
        user.setRoles(new ArrayList<>());
        return user;
    }

    private static Review review(String id, User author) {
        Unit unit = new Unit();
        unit.setId("unit-1");
        unit.setCode("ISYS1000");
        unit.setName("Intro to Systems");
        Review review = new Review();
        review.setId(id);
        review.setUnit(unit);
        review.setUser(author);
        review.setRating(4);
        review.setFinalGrade(78);
        review.setReviewText("Line one.\nLine two.");
        review.setTermType(AcademicTerm.SEMESTER_2);
        review.setTermYear(2025);
        review.setProfessor("Dr Example");
        review.setWorkload(7);
        review.setHasExam(true);
        review.setWouldTakeAgain(false);
        review.setTags(Set.of(ReviewTag.WEEKLY_QUIZZES, ReviewTag.GROUP_WORK));
        review.setLikeCount(3);
        return review;
    }
}

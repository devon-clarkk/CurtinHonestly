package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.RecognitionTier;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.ReviewerTier;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.ReviewerProfileDTO;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.util.ReviewerRank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test with mocked repositories, no Spring context. Checks the
 * batching contract (one query per call, every requested id answered) and the
 * shape of the profile handed to the signed-in user.
 */
class ReviewerRankServiceTest {

    private ReviewRepo reviewRepo;
    private UserRepo userRepo;
    private ReviewerRankService service;

    @BeforeEach
    void setUp() {
        reviewRepo = mock(ReviewRepo.class);
        userRepo = mock(UserRepo.class);
        service = new ReviewerRankService(reviewRepo, userRepo);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** A plain implementation rather than a mock, so rows can be built inside a stubbing call. */
    private record StatsRow(String userId, Long reviewCount, Long likesReceived) implements ReviewRepo.ReviewerStats {
        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public Long getReviewCount() {
            return reviewCount;
        }

        @Override
        public Long getLikesReceived() {
            return likesReceived;
        }
    }

    private static ReviewRepo.ReviewerStats stats(String userId, Long reviews, Long likes) {
        return new StatsRow(userId, reviews, likes);
    }

    private static User userWithId(String userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }

    private static Review reviewBy(String userId) {
        Review review = new Review();
        if (userId != null) {
            review.setUser(userWithId(userId));
        }
        return review;
    }

    @Test
    void ranksForAnswersEveryRequestedIdWithOneQuery() {
        List<ReviewRepo.ReviewerStats> rows = List.of(stats("busy", 12L, 20L), stats("quiet", 1L, 0L));
        when(reviewRepo.aggregateReviewerStats(anyCollection())).thenReturn(rows);

        Map<String, ReviewerRank> ranks = service.ranksFor(List.of("busy", "quiet", "silent"));

        verify(reviewRepo, times(1)).aggregateReviewerStats(anyCollection());
        assertEquals(3, ranks.size());
        assertEquals(ReviewerTier.TOP_REVIEWER, ranks.get("busy").activityTier());
        assertEquals(RecognitionTier.VALUED_REVIEWER, ranks.get("busy").recognitionTier());
        assertEquals(ReviewerTier.NEWCOMER, ranks.get("quiet").activityTier());
        assertNull(ranks.get("quiet").recognitionTier());
        assertEquals(ReviewerRank.NONE, ranks.get("silent"));
    }

    @Test
    void ranksForAuthorsOfSkipsAnonymisedReviewsAndDeduplicatesAuthors() {
        List<ReviewRepo.ReviewerStats> rows = List.of(stats("a", 2L, 3L));
        when(reviewRepo.aggregateReviewerStats(anyCollection())).thenReturn(rows);

        Map<String, ReviewerRank> ranks = service.ranksForAuthorsOf(
                List.of(reviewBy("a"), reviewBy(null), reviewBy("a"), reviewBy("b")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(reviewRepo).aggregateReviewerStats(captor.capture());
        assertEquals(Set.of("a", "b"), Set.copyOf(captor.getValue()));
        assertEquals(2, captor.getValue().size());

        assertEquals(ReviewerTier.NEWCOMER, ranks.get("a").activityTier());
        assertEquals(ReviewerRank.NONE, ranks.get("b"));
        assertEquals(2, ranks.size());
    }

    @Test
    void emptyOrBlankInputNeverHitsTheDatabase() {
        assertTrue(service.ranksFor(List.of()).isEmpty());
        assertTrue(service.ranksForAuthorsOf(List.of()).isEmpty());
        assertTrue(service.ranksForAuthorsOf(List.of(reviewBy(null))).isEmpty());
        assertEquals(ReviewerRank.NONE, service.rankFor(" "));
        assertEquals(ReviewerRank.NONE, service.rankFor(null));
        verify(reviewRepo, never()).aggregateReviewerStats(any());
    }

    @Test
    void nullAggregatesReadAsZero() {
        List<ReviewRepo.ReviewerStats> rows = List.of(stats("x", null, null));
        when(reviewRepo.aggregateReviewerStats(anyCollection())).thenReturn(rows);

        ReviewerRank rank = service.rankFor("x");

        assertEquals(0, rank.reviewCount());
        assertEquals(0, rank.likesReceived());
        assertEquals(ReviewerTier.LURKER, rank.activityTier());
    }

    @Test
    void profileForCurrentUserResolvesTheSignedInUserByEmailAndExposesNoIdentity() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student@example.com", null, List.of()));
        when(userRepo.findByEmail("student@example.com")).thenReturn(Optional.of(userWithId("user-1")));
        List<ReviewRepo.ReviewerStats> rows = List.of(stats("user-1", 7L, 5L));
        when(reviewRepo.aggregateReviewerStats(anyCollection())).thenReturn(rows);

        ReviewerProfileDTO profile = service.profileForCurrentUser();

        assertEquals(ReviewerTier.REGULAR, profile.activityTier());
        assertEquals("Regular", profile.activityTierLabel());
        assertEquals(RecognitionTier.APPRECIATED, profile.recognitionTier());
        assertEquals("Appreciated", profile.recognitionTierLabel());
        assertEquals(7, profile.reviewCount());
        assertEquals(5, profile.likesReceived());
        assertEquals(3, profile.reviewsToNextTier());
        assertEquals("Top Reviewer", profile.nextTierLabel());
        assertEquals(10, profile.nextTierThreshold());
        assertEquals(10, profile.likesToNextRecognition());
        assertEquals("Valued Reviewer", profile.nextRecognitionLabel());
        assertEquals(15, profile.nextRecognitionThreshold());
    }

    @Test
    void profileAtTheTopOfBothLaddersHasNoNextStep() {
        ReviewerProfileDTO profile = ReviewerProfileDTO.from(ReviewerRank.of(40, 120));

        assertEquals("Legend", profile.activityTierLabel());
        assertEquals("Community Favourite", profile.recognitionTierLabel());
        assertEquals(0, profile.reviewsToNextTier());
        assertNull(profile.nextTierLabel());
        assertEquals(0, profile.nextTierThreshold());
        assertEquals(0, profile.likesToNextRecognition());
        assertNull(profile.nextRecognitionLabel());
        assertEquals(0, profile.nextRecognitionThreshold());
    }
}

package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.ReviewFlag;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.FlaggedReviewDTO;
import com.curtinhonestly.backend.repo.ReviewFlagRepo;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewFlagServiceTest {

    @Mock ReviewFlagRepo flagRepo;
    @Mock ReviewRepo reviewRepo;
    @Mock UserRepo userRepo;

    @Captor ArgumentCaptor<ReviewFlag> flagCaptor;

    private ReviewFlagService service() {
        return new ReviewFlagService(flagRepo, reviewRepo, userRepo);
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(email, "pw", List.of())));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Review review() {
        Unit unit = new Unit();
        unit.setId("unit-1");
        unit.setCode("ISYS1000");
        Review review = new Review();
        review.setId("review-1");
        review.setUnit(unit);
        review.setReviewText("Some review text.");
        return review;
    }

    private User user() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("alice@student.curtin.edu.au");
        return user;
    }

    @Test
    void flagReview_savesFlagWithTrimmedReason() {
        authenticateAs("alice@student.curtin.edu.au");
        Review review = review();
        when(reviewRepo.findById("review-1")).thenReturn(Optional.of(review));
        when(userRepo.findByEmail("alice@student.curtin.edu.au")).thenReturn(Optional.of(user()));
        when(flagRepo.existsByUser_IdAndReview_Id("user-1", "review-1")).thenReturn(false);

        service().flagReview("review-1", "  Contains a slur.  ");

        verify(flagRepo).save(flagCaptor.capture());
        ReviewFlag saved = flagCaptor.getValue();
        assertThat(saved.getReview().getId()).isEqualTo("review-1");
        assertThat(saved.getUser().getId()).isEqualTo("user-1");
        assertThat(saved.getReason()).isEqualTo("Contains a slur.");
    }

    @Test
    void flagReview_treatsBlankReasonAsNull() {
        authenticateAs("alice@student.curtin.edu.au");
        when(reviewRepo.findById("review-1")).thenReturn(Optional.of(review()));
        when(userRepo.findByEmail("alice@student.curtin.edu.au")).thenReturn(Optional.of(user()));
        when(flagRepo.existsByUser_IdAndReview_Id("user-1", "review-1")).thenReturn(false);

        service().flagReview("review-1", "   ");

        verify(flagRepo).save(flagCaptor.capture());
        assertThat(flagCaptor.getValue().getReason()).isNull();
    }

    @Test
    void flagReview_isIdempotentForAnAlreadyFlaggedReview() {
        authenticateAs("alice@student.curtin.edu.au");
        when(reviewRepo.findById("review-1")).thenReturn(Optional.of(review()));
        when(userRepo.findByEmail("alice@student.curtin.edu.au")).thenReturn(Optional.of(user()));
        when(flagRepo.existsByUser_IdAndReview_Id("user-1", "review-1")).thenReturn(true);

        service().flagReview("review-1", "again");

        verify(flagRepo, never()).save(any());
    }

    @Test
    void flagReview_rejectsUnknownReview() {
        when(reviewRepo.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().flagReview("nope", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(flagRepo, never()).save(any());
    }

    @Test
    void getFlaggedReviews_ordersByRepoOrderAndIncludesFlagCounts() {
        when(flagRepo.findDistinctFlaggedReviewIdsOrderByFlagCountDesc()).thenReturn(List.of("review-1"));
        when(reviewRepo.findById("review-1")).thenReturn(Optional.of(review()));
        when(flagRepo.countByReview_Id("review-1")).thenReturn(3L);

        List<FlaggedReviewDTO> result = service().getFlaggedReviews();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).reviewId()).isEqualTo("review-1");
        assertThat(result.get(0).unitCode()).isEqualTo("ISYS1000");
        assertThat(result.get(0).flagCount()).isEqualTo(3L);
    }

    @Test
    void getFlaggedReviews_skipsReviewsThatNoLongerExist() {
        when(flagRepo.findDistinctFlaggedReviewIdsOrderByFlagCountDesc()).thenReturn(List.of("gone"));
        when(reviewRepo.findById("gone")).thenReturn(Optional.empty());

        assertThat(service().getFlaggedReviews()).isEmpty();
    }

    @Test
    void dismissFlags_delegatesToRepo() {
        service().dismissFlags("review-1");
        verify(flagRepo).deleteByReview_Id("review-1");
    }
}

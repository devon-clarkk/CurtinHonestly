package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.AcademicTerm;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.ReviewUpdateRequest;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.service.recommendation.RecommendationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceUpdateTest {

    @Mock ReviewRepo reviewRepo;
    @Mock UnitService unitService;
    @Mock UserRepo userRepo;
    @Mock UnitRepo unitRepo;
    @Mock ProfanityFilterService profanityFilterService;
    @Mock UnitAggregateService unitAggregateService;
    @Mock CampaignService campaignService;
    @Mock RecommendationService recommendationService;

    private ReviewService service() {
        return new ReviewService(reviewRepo, unitService, userRepo, unitRepo,
                profanityFilterService, unitAggregateService, campaignService, recommendationService);
    }

    private Review existingReview() {
        Unit unit = new Unit();
        unit.setId("unit-1");
        unit.setCode("ISYS1000");

        User author = new User();
        author.setId("user-1");

        Review review = new Review();
        review.setId("review-1");
        review.setUnit(unit);
        review.setUser(author);
        review.setRating(3);
        review.setReviewText("Original text.");
        review.setWorkload(5);
        return review;
    }

    private ReviewUpdateRequest updateRequest() {
        return new ReviewUpdateRequest(5, 90, "Updated text — much better than I first thought.",
                AcademicTerm.SEMESTER_2, 2026, "Dr Smith", 7, true, true, null);
    }

    @Test
    void updateReview_appliesAllEditableFieldsAndRecalculatesTheUnit() {
        Review review = existingReview();
        when(reviewRepo.findById("review-1")).thenReturn(Optional.of(review));
        when(profanityFilterService.containsProfanity(anyString())).thenReturn(false);
        when(reviewRepo.save(review)).thenReturn(review);

        Review result = service().updateReview("review-1", updateRequest());

        assertThat(result.getRating()).isEqualTo(5);
        assertThat(result.getFinalGrade()).isEqualTo(90);
        assertThat(result.getReviewText()).isEqualTo("Updated text — much better than I first thought.");
        assertThat(result.getTermType()).isEqualTo(AcademicTerm.SEMESTER_2);
        assertThat(result.getTermYear()).isEqualTo(2026);
        assertThat(result.getProfessor()).isEqualTo("Dr Smith");
        assertThat(result.getWorkload()).isEqualTo(7);
        assertThat(result.isHasExam()).isTrue();
        assertThat(result.isWouldTakeAgain()).isTrue();
        verify(unitAggregateService).recalculateForUnit("unit-1");
    }

    @Test
    void updateReview_neverChangesUnitAuthorOrId() {
        Review review = existingReview();
        when(reviewRepo.findById("review-1")).thenReturn(Optional.of(review));
        when(profanityFilterService.containsProfanity(anyString())).thenReturn(false);
        when(reviewRepo.save(review)).thenReturn(review);

        Review result = service().updateReview("review-1", updateRequest());

        assertThat(result.getId()).isEqualTo("review-1");
        assertThat(result.getUnit().getCode()).isEqualTo("ISYS1000");
        assertThat(result.getUser().getId()).isEqualTo("user-1");
    }

    @Test
    void updateReview_rejectsProfanityWithoutSavingOrRecalculating() {
        Review review = existingReview();
        when(reviewRepo.findById("review-1")).thenReturn(Optional.of(review));
        when(profanityFilterService.containsProfanity(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service().updateReview("review-1", updateRequest()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reviewRepo, never()).save(any());
        verifyNoInteractions(unitAggregateService);
    }

    @Test
    void updateReview_rejectsUnknownReview() {
        when(reviewRepo.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateReview("nope", updateRequest()))
                .isInstanceOf(RuntimeException.class);
        verify(reviewRepo, never()).save(any());
    }
}

package com.curtinhonestly.backend.mapper;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.ReviewTag;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.dto.TagSummaryDTO;
import com.curtinhonestly.backend.dto.UnitDetailsDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UnitMapperTagSummaryTest {

    private Review reviewWithTags(ReviewTag... tags) {
        Review review = new Review();
        review.setId("review-" + System.identityHashCode(tags));
        review.setTags(Set.of(tags));
        return review;
    }

    @Test
    void tagSummary_countsAcrossReviewsAndSortsByCountDescending() {
        Unit unit = new Unit();
        unit.setId("unit-1");
        unit.setCode("ISYS1000");
        unit.setReviews(List.of(
                reviewWithTags(ReviewTag.GROUP_WORK, ReviewTag.WEEKLY_QUIZZES),
                reviewWithTags(ReviewTag.GROUP_WORK),
                reviewWithTags(ReviewTag.RECORDED_LECTURES)
        ));

        UnitDetailsDTO dto = UnitMapper.toDetailsDTO(unit);

        assertThat(dto.getTagSummary())
                .extracting(TagSummaryDTO::tag, TagSummaryDTO::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("GROUP_WORK", 2),
                        org.assertj.core.groups.Tuple.tuple("RECORDED_LECTURES", 1),
                        org.assertj.core.groups.Tuple.tuple("WEEKLY_QUIZZES", 1)
                );
    }

    @Test
    void tagSummary_isEmptyWhenNoReviewsHaveTags() {
        Unit unit = new Unit();
        unit.setId("unit-1");
        unit.setCode("ISYS1000");
        Review untagged = new Review();
        untagged.setId("review-1");
        unit.setReviews(List.of(untagged));

        UnitDetailsDTO dto = UnitMapper.toDetailsDTO(unit);

        assertThat(dto.getTagSummary()).isEmpty();
    }

    @Test
    void tagSummary_usesTheEnumsDisplayNameAsTheLabel() {
        Unit unit = new Unit();
        unit.setId("unit-1");
        unit.setCode("ISYS1000");
        unit.setReviews(List.of(reviewWithTags(ReviewTag.PROCTORED_EXAM)));

        UnitDetailsDTO dto = UnitMapper.toDetailsDTO(unit);

        assertThat(dto.getTagSummary()).hasSize(1);
        assertThat(dto.getTagSummary().get(0).label()).isEqualTo("Proctored exam");
    }
}

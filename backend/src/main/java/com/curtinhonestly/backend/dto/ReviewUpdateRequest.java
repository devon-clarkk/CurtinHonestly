package com.curtinhonestly.backend.dto;

import com.curtinhonestly.backend.domain.AcademicTerm;
import com.curtinhonestly.backend.domain.ReviewTag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Set;

// Same user-settable fields as ReviewCreateRequest, minus unitCode — you can't
// move a review to a different unit on edit. createdAt/id/unit/user are never
// touched here either.
public record ReviewUpdateRequest(
        @Min(1) @Max(5) int rating,
        @Min(0) @Max(100) Integer finalGrade,
        @Size(max = 2000) String reviewText,
        AcademicTerm termType,
        @Min(2000) @Max(2100) Integer termYear,
        // 200 rather than a tighter bound so an existing review with a long
        // professor string never becomes un-editable (a create-side cap can't
        // retroactively 400 an edit of already-stored data).
        @Size(max = 200) String professor,
        @Min(0) @Max(10) int workload,
        boolean hasExam,
        boolean wouldTakeAgain,
        Set<ReviewTag> tags
) {}

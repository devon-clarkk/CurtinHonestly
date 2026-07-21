package com.curtinhonestly.backend.dto;

import com.curtinhonestly.backend.domain.ReviewTag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

// Only the fields a user is allowed to set on a review. createdAt and id are
// always assigned server-side - never bind the Review entity directly here.
public record ReviewCreateRequest(
        @Min(1) @Max(5) int rating,
        @Min(0) @Max(100) Integer finalGrade,
        @Size(max = 2000) String reviewText,
        String semesterTaken,
        String professor,
        @Min(0) @Max(10) int workload,
        boolean hasExam,
        boolean wouldTakeAgain,
        @NotBlank String unitCode,
        Set<ReviewTag> tags
) {}

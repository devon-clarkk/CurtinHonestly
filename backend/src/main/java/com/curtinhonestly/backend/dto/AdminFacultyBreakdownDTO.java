package com.curtinhonestly.backend.dto;

// Per-faculty coverage: how much of the catalogue has at least one review.
public record AdminFacultyBreakdownDTO(
        String faculty,
        String label,
        long units,
        long unitsWithReviews,
        long reviews
) {}

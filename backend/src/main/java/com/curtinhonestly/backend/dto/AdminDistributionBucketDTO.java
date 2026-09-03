package com.curtinhonestly.backend.dto;

// One bar of a histogram: a display label and how many reviews fell in it.
public record AdminDistributionBucketDTO(
        String label,
        long count
) {}

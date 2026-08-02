package com.curtinhonestly.backend.dto;

public record FlaggedReviewDTO(String reviewId, String unitCode, String reviewText, long flagCount) {}

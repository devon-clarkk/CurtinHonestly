package com.curtinhonestly.backend.dto;

import jakarta.validation.constraints.Size;

public record FlagReviewRequest(@Size(max = 500) String reason) {}

package com.curtinhonestly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class CreateReviewResponseDTO {
    private ReviewDTO review;
    private String campaignEntryToken;
    private String campaignName;
}

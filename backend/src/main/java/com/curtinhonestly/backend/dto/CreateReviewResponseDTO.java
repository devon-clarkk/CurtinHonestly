package com.curtinhonestly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateReviewResponseDTO {
    private ReviewDTO review;
    // A review can earn entries in several campaigns at once. The first token/name
    // are surfaced for the toast; newEntryCount is the total earned this submission.
    private String campaignEntryToken;
    private String campaignName;
    private int newEntryCount;
}

package com.curtinhonestly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CampaignProgressDTO {
    private int qualifyingReviews;
    private int requiredReviews;
    private int entriesEarned;
    private int maxEntries;
    private boolean requireVerifiedStudent;
}

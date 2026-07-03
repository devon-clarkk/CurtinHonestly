package com.curtinhonestly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
public class AccountDTO {
    private String email;
    private boolean verifiedStudent;
    private String campaignName;
    private String campaignPrizeDescription;
    private Instant campaignEndsAt;
    private List<CampaignEntrySummaryDTO> campaignEntries;
}

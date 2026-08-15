package com.curtinhonestly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AccountDTO {
    private String email;
    private boolean verifiedStudent;
    // All campaigns the user has joined (each with its own prize + progress), plus
    // the flat list of every draw entry they hold across those campaigns.
    private List<CampaignMembershipDTO> campaigns;
    private List<CampaignEntrySummaryDTO> campaignEntries;
}

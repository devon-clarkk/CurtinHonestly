package com.curtinhonestly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AccountDTO {
    private String email;
    private boolean verifiedStudent;
    // The account's current roles ("ROLE_USER", "ROLE_CLUB", "ROLE_ADMIN"). The JWT
    // carries the roles at sign-in time; this is what the frontend refreshes from
    // when an admin has since granted club access, so the user need not sign in again.
    private List<String> roles;
    // All campaigns the user has joined (each with its own prize + progress), plus
    // the flat list of every draw entry they hold across those campaigns.
    private List<CampaignMembershipDTO> campaigns;
    private List<CampaignEntrySummaryDTO> campaignEntries;
}

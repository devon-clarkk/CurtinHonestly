package com.curtinhonestly.backend.dto;

import java.time.Instant;
import java.util.List;

// Admin view of a referral link: its tracking counts plus the campaigns it
// enrols signups into (empty = a pure tracking link).
public record ReferralLinkAdminDTO(
        String id,
        String slug,
        String name,
        String landingPath,
        boolean active,
        long visitCount,
        long signupCount,
        long reviewCount,
        List<CampaignRef> campaigns,
        Instant createdAt
) {
    public record CampaignRef(String id, String name) {}
}

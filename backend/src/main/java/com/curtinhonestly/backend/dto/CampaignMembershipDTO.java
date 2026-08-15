package com.curtinhonestly.backend.dto;

import java.time.Instant;

// One campaign a user has joined, with that campaign's prize and the user's
// progress toward it. A user can hold several of these at once.
public record CampaignMembershipDTO(
        String name,
        String prizeDescription,
        Instant endsAt,
        CampaignProgressDTO progress
) {}

package com.curtinhonestly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class CampaignAdminDTO {
    private String id;
    private String slug;
    private String code;
    private String name;
    private String prizeDescription;
    private Instant startsAt;
    private Instant endsAt;
    private boolean active;
    private Integer maxRedemptions;
    private int minReviewLength;
    private int maxEntriesPerUser;
    private boolean requireVerifiedStudent;
    private int requiredReviewCount;
    private long signupCount;
    private long entryCount;
    private Instant createdAt;
}

package com.curtinhonestly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class CampaignEntrySummaryDTO {
    private String entryToken;
    private String campaignName;
    private String unitCode;
    private Instant createdAt;
}

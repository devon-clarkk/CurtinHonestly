package com.curtinhonestly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class CampaignValidationDTO {
    private boolean valid;
    private String message;
    private String campaignName;
    private String prizeDescription;
    private Instant endsAt;
}

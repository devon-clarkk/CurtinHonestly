package com.curtinhonestly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class CampaignEntryAdminDTO {
    private String id;
    private String entryToken;
    private String userEmail;
    private String unitCode;
    private Instant createdAt;
}

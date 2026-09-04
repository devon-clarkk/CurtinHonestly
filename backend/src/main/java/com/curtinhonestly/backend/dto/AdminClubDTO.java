package com.curtinhonestly.backend.dto;

import java.time.Instant;
import java.util.List;

/** A club in the admin app, with its members (emails included: admins already see emails). */
public record AdminClubDTO(
        String id,
        String name,
        String slug,
        String description,
        String websiteUrl,
        String logoUrl,
        String contactEmail,
        boolean trusted,
        boolean active,
        Instant createdAt,
        long eventCount,
        long pendingCount,
        List<AdminClubMemberDTO> members
) {}

package com.curtinhonestly.backend.dto;

import java.time.Instant;

public record AdminClubMemberDTO(
        String userId,
        String email,
        String role,
        Instant createdAt
) {}

package com.curtinhonestly.backend.dto;

import java.time.Instant;
import java.util.List;

public record UserAdminDTO(
        String id,
        String email,
        String username,
        List<String> roles,
        boolean banned,
        long reviewCount,
        Instant createdAt
) {}

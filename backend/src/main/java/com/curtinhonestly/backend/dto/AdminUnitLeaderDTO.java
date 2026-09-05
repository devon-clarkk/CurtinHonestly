package com.curtinhonestly.backend.dto;

public record AdminUnitLeaderDTO(
        String code,
        String name,
        long reviewCount,
        double averageRating
) {}

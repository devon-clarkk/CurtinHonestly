package com.curtinhonestly.backend.dto;

public record TimeSeriesPointDTO(
        String period,
        long users,
        long reviews
) {}

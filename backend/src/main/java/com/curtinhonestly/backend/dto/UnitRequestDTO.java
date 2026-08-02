package com.curtinhonestly.backend.dto;

import java.time.Instant;

public record UnitRequestDTO(String id, String requestedCode, String note, Instant createdAt) {}

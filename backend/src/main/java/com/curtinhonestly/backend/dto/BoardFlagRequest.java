package com.curtinhonestly.backend.dto;

import jakarta.validation.constraints.Size;

public record BoardFlagRequest(@Size(max = 300) String reason) {}

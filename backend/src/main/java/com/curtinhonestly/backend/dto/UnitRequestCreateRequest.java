package com.curtinhonestly.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnitRequestCreateRequest(
        @NotBlank @Size(max = 100) String requestedCode,
        @Size(max = 500) String note
) {}

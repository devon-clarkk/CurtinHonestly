package com.curtinhonestly.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BoardThreadCreateRequest(
        @NotBlank @Size(max = 140) String title,
        @NotBlank @Size(max = 4000) String body
) {}

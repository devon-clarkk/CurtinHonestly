package com.curtinhonestly.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BoardPostCreateRequest(@NotBlank @Size(max = 4000) String body) {}

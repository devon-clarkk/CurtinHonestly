package com.curtinhonestly.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipCreateRequest(@NotBlank @Size(max = 200) String text) {}

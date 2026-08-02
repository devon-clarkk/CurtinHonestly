package com.curtinhonestly.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CompletedUnitsUpdateRequest(@NotNull @Size(max = 200) Set<String> unitCodes) {}

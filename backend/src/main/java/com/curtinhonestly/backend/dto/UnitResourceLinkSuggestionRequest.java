package com.curtinhonestly.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A signed-in student proposing a link for one unit. {@code kind} is a
 * ResourceKind name; it is parsed in the service so a typo is a 400 with a
 * readable message rather than a deserialisation failure.
 */
public record UnitResourceLinkSuggestionRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 600) String url,
        @Size(max = 300) String description,
        @NotBlank String kind,
        @Size(max = 300) String note
) {}

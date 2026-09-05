package com.curtinhonestly.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Edit body for threads and posts. Title is optional and ignored for posts. */
public record BoardContentUpdateRequest(
        @Size(max = 140) String title,
        @NotBlank @Size(max = 4000) String body
) {}

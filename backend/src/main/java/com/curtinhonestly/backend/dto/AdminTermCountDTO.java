package com.curtinhonestly.backend.dto;

// Reviews per teaching period. The label is built client-side from
// (termType, termYear), matching the public site.
public record AdminTermCountDTO(
        String termType,
        Integer termYear,
        long count
) {}

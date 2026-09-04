package com.curtinhonestly.backend.dto;

import java.util.List;

/** The unit page's discussion preview: counts plus the three most active threads. */
public record BoardUnitSummaryDTO(
        String unitCode,
        String unitName,
        long threadCount,
        long postCount,
        List<BoardThreadSummaryDTO> latestThreads
) {}

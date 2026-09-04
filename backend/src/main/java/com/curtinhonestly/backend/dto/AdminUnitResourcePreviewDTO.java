package com.curtinhonestly.backend.dto;

import java.util.List;

/**
 * What a targeting rule would match, so an admin can sanity check a prefix
 * list before saving: the total count, up to ten sample codes, and the scope
 * label the public page would show.
 */
public record AdminUnitResourcePreviewDTO(int matchCount, List<String> sampleCodes, String scopeLabel) {}

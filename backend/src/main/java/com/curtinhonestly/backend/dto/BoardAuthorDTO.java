package com.curtinhonestly.backend.dto;

import com.curtinhonestly.backend.domain.RecognitionTier;
import com.curtinhonestly.backend.domain.ReviewerTier;

/**
 * Everything the public sees about who wrote a thread or post. The pseudonym
 * is a keyed hash of the user id (see util.Pseudonym), so it is stable across
 * a user's posts without exposing the id or email. Tiers are null when the
 * author is anonymised or has no reviewing activity, matching ReviewDTO.
 */
public record BoardAuthorDTO(
        String pseudonym,
        boolean verifiedStudent,
        ReviewerTier tier,
        String tierLabel,
        RecognitionTier recognition,
        String recognitionLabel
) {}

package com.curtinhonestly.backend.service;

// Thrown when CampaignService can't find an unused entry token after several
// attempts. Distinct from IllegalStateException so GlobalExceptionHandler can
// map it to 503 (retry later) instead of 400 (client's request was invalid).
public class CampaignEntryTokenExhaustedException extends RuntimeException {
    public CampaignEntryTokenExhaustedException(String message) {
        super(message);
    }
}

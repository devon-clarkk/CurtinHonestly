package com.curtinhonestly.backend.dto;

import java.util.List;
import java.util.Map;

public record AdminAnalyticsDTO(
        List<TimeSeriesPointDTO> signupsAndReviewsOverTime,
        Map<String, Long> reviewsByFaculty,
        double averageWorkload,
        double wouldTakeAgainRatio
) {}

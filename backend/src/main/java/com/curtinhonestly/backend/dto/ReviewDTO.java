package com.curtinhonestly.backend.dto;

import com.curtinhonestly.backend.domain.AcademicTerm;
import com.curtinhonestly.backend.domain.RecognitionTier;
import com.curtinhonestly.backend.domain.ReviewTag;
import com.curtinhonestly.backend.domain.ReviewerTier;
import lombok.Data;

import java.time.Instant;
import java.util.Set;

@Data
public class ReviewDTO {

    private String id;

    // Review details
    private int rating;
    private Integer finalGrade;
    private String reviewText;
    // The client builds the display label from these. Deliberately not sent as a
    // formatted string - that is what froze the old format into the database.
    private AcademicTerm termType;
    private Integer termYear;
    private String professor;
    private int workload;
    private boolean hasExam;
    private boolean wouldTakeAgain;
    private Set<ReviewTag> tags;

    private int likeCount;
    private boolean likedByCurrentUser;

    private boolean reviewerVerified;

    // Reviewer standing (ReviewerRank), by tier name plus its display label. Null
    // when the review is anonymised (author deleted their account) or the author
    // has no standing to show. Recognition stays null until it is earned.
    private ReviewerTier reviewerTier;
    private String reviewerTierLabel;
    private RecognitionTier reviewerRecognition;
    private String reviewerRecognitionLabel;

    private Instant createdAt;
}

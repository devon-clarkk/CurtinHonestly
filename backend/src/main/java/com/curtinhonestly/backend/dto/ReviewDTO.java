package com.curtinhonestly.backend.dto;

import com.curtinhonestly.backend.domain.ReviewTag;
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
    private String semesterTaken;
    private String professor;
    private int workload;
    private boolean hasExam;
    private boolean wouldTakeAgain;
    private Set<ReviewTag> tags;

    private int likeCount;
    private boolean likedByCurrentUser;

    private boolean reviewerVerified;
    private Instant createdAt;
}

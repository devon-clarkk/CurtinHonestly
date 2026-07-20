package com.curtinhonestly.backend.dto;

import lombok.Data;

import java.time.Instant;

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

    private int likeCount;
    private boolean likedByCurrentUser;

    private boolean reviewerVerified;
    private Instant createdAt;
}

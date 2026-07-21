package com.curtinhonestly.backend.domain;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity

// Map to the "reviews" table
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_reviews_user_unit", columnNames = {"user_id", "unit_id"})
)

// Lombok getters/setters
@Getter
@Setter

// Lombok constructors
@NoArgsConstructor
@AllArgsConstructor

// Json setup
@JsonInclude(JsonInclude.Include.NON_DEFAULT)

// Review class - Holds data for CurtinHonestly reviews.
public class Review {
    @Id
    @UuidGenerator
    private String id;

    private int rating; // 1-5
    private Integer finalGrade; // Optional
    @Column(length = 2000)
    private String reviewText;
    private String semesterTaken; // Optional
    private String professor; // Optional

    private int workload; // 0-10
    private boolean hasExam; // Optional
    private boolean wouldTakeAgain;

    // Denormalized count of rows in review_likes for this review. Kept in sync by ReviewLikeService.
    @Column(nullable = false)
    private int likeCount = 0;

    // Predefined assessment/experience tags (review-experience.md #4) — a
    // fixed enum set, mirroring User.roles' @ElementCollection pattern.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "review_tags", joinColumns = @JoinColumn(name = "review_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false)
    private Set<ReviewTag> tags = new HashSet<>();

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();

    @ManyToOne
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    // Nullable: account deletion anonymizes reviews (detaches the author) by
    // default rather than deleting them — the review content is the site's
    // asset, not the identity. @OnDelete mirrors that at the DB level too, so
    // even a row deleted outside the app doesn't orphan-delete its reviews.
    @ManyToOne
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JsonIgnore
    private User user;

}


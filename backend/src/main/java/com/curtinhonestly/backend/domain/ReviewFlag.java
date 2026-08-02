package com.curtinhonestly.backend.domain;

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

/**
 * One user's "report" on a review — one per user per review. Moderation today
 * is admins proactively reading the panel; this surfaces flagged reviews to
 * them instead (review-experience.md #8).
 */
@Entity
@Table(
        name = "review_flags",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_flags_user_review",
                columnNames = {"user_id", "review_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ReviewFlag {

    @Id
    @UuidGenerator
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Review review;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();
}

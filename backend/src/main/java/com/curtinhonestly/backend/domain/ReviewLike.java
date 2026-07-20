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

@Entity
@Table(
        name = "review_likes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_likes_user_review",
                columnNames = {"user_id", "review_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ReviewLike {

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

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();
}

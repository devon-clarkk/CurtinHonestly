package com.curtinhonestly.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * A reply in a {@link BoardThread}. Soft deleted (deletedAt) rather than
 * removed so the surrounding conversation still reads in order; the public
 * DTO renders a deleted post as a "[removed]" placeholder.
 */
@Entity
@Table(
        name = "board_posts",
        indexes = @Index(name = "ix_board_posts_thread_created", columnList = "thread_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
public class BoardPost {

    @Id
    @UuidGenerator
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BoardThread thread;

    @ManyToOne
    @JoinColumn(name = "author_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JsonIgnore
    private User author;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();

    @Column(columnDefinition = "TIMESTAMPTZ")
    private Instant editedAt;

    @Column(columnDefinition = "TIMESTAMPTZ")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}

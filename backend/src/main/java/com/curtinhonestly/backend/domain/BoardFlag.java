package com.curtinhonestly.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * One student's report on a thread or a post: exactly one of the two targets
 * is set. One flag per reporter per target; Postgres treats NULLs as distinct
 * in unique constraints, so a reporter's post flags never collide with their
 * thread flags. Mirrors {@link ReviewFlag}.
 */
@Entity
@Table(
        name = "board_flags",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_board_flags_reporter_thread", columnNames = {"reporter_id", "thread_id"}),
                @UniqueConstraint(name = "uk_board_flags_reporter_post", columnNames = {"reporter_id", "post_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class BoardFlag {

    @Id
    @UuidGenerator
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User reporter;

    @ManyToOne
    @JoinColumn(name = "thread_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BoardThread thread;

    @ManyToOne
    @JoinColumn(name = "post_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BoardPost post;

    @Column(length = 300)
    private String reason;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();
}

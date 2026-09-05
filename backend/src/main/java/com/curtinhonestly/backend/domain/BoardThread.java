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
 * A discussion thread on the general board or on one unit's board. Replies are
 * {@link BoardPost}s. Removal is a soft delete (deletedAt) so the thread's id
 * keeps resolving for moderation while it disappears from every public list.
 *
 * Every NOT NULL column carries a DB default: ddl-auto=update adds columns to
 * populated tables and Postgres rejects ADD COLUMN ... NOT NULL without one
 * (see Review.likeCount).
 */
@Entity
@Table(
        name = "board_threads",
        indexes = {
                @Index(name = "ix_board_threads_unit_activity", columnList = "unit_id, last_activity_at"),
                @Index(name = "ix_board_threads_scope_activity", columnList = "scope, last_activity_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class BoardThread {

    @Id
    @UuidGenerator
    private String id;

    // Null for GENERAL threads, required for UNIT threads (enforced in BoardService).
    @ManyToOne
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, columnDefinition = "VARCHAR(16) DEFAULT 'GENERAL' NOT NULL")
    private BoardScope scope = BoardScope.GENERAL;

    @Column(nullable = false, length = 140)
    private String title;

    @Column(nullable = false, length = 4000)
    private String body;

    // Nullable: account deletion detaches the author, the thread stays. Same
    // policy as Review.user.
    @ManyToOne
    @JoinColumn(name = "author_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JsonIgnore
    private User author;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();

    @Column(columnDefinition = "TIMESTAMPTZ")
    private Instant editedAt;

    // Bumped on every new reply so boards sort by "most recently discussed".
    @Column(name = "last_activity_at", nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant lastActivityAt = Instant.now();

    // Denormalised count of non-deleted posts, kept in step by BoardService.
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0 NOT NULL")
    private int replyCount = 0;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE NOT NULL")
    private boolean pinned = false;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE NOT NULL")
    private boolean locked = false;

    @Column(columnDefinition = "TIMESTAMPTZ")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}

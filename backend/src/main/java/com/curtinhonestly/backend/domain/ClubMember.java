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
 * Links a user account to a club with a role. A user can belong to several
 * clubs. Deleting the club or the user removes the membership at the database
 * level; ClubService keeps the user's ROLE_CLUB in step when it removes rows
 * itself.
 */
@Entity
@Table(name = "club_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_club_members_club_user", columnNames = {"club_id", "user_id"}),
        indexes = @Index(name = "idx_club_members_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class ClubMember {

    @Id
    @UuidGenerator
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'EDITOR' NOT NULL")
    private ClubMemberRole role = ClubMemberRole.EDITOR;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();
}

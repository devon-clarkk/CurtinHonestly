package com.curtinhonestly.backend.domain;

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
 * A single-use, hashed token emailed to a user to prove ownership of an inbox.
 * Only the SHA-256 hash of the token is stored — the raw value lives only in the
 * emailed link.
 */
@Entity
@Table(
        name = "verification_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_verification_tokens_hash", columnNames = {"token_hash"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerificationToken {

    @Id
    @UuidGenerator
    private String id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationPurpose purpose;

    /**
     * The email address this token, once confirmed, should apply to the account
     * (e.g. the student email to switch to and mark verified). May equal the
     * account's current email when it was already registered with a student address.
     */
    @Column(name = "target_email")
    private String targetEmail;

    @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ NOT NULL")
    private Instant expiresAt;

    @Column(name = "used_at", columnDefinition = "TIMESTAMPTZ")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
